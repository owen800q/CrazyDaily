/*
  Copyright 2017 Sun Jian
  <p>
  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at
  <p>
  http://www.apache.org/licenses/LICENSE-2.0
  <p>
  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
 */
package com.crazysunj.crazydaily.ui.home;

import android.Manifest;
import android.animation.ArgbEvaluator;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import com.crazysunj.cardslideview.CardViewPager;
import com.crazysunj.crazydaily.R;
import com.crazysunj.crazydaily.base.BaseFragment;
import com.crazysunj.crazydaily.constant.WeexConstant;
import com.crazysunj.crazydaily.di.module.EntityModule;
import com.crazysunj.crazydaily.entity.CityEntity;
import com.crazysunj.crazydaily.module.permission.PermissionCamera;
import com.crazysunj.crazydaily.module.permission.PermissionHelper;
import com.crazysunj.crazydaily.module.permission.PermissionStorage;
import com.crazysunj.crazydaily.presenter.NewHomePresenter;
import com.crazysunj.crazydaily.presenter.contract.NewHomeContract;
import com.crazysunj.crazydaily.service.DownloadService;
import com.crazysunj.crazydaily.ui.adapter.HomeAdapter;
import com.crazysunj.crazydaily.ui.adapter.helper.HomeAdapterHelper;
import com.crazysunj.crazydaily.ui.browser.BrowserActivity;
import com.crazysunj.crazydaily.ui.scan.ScannerActivity;
import com.crazysunj.crazydaily.util.SnackbarUtil;
import com.crazysunj.crazydaily.view.banner.BannerCardHandler;
import com.crazysunj.crazydaily.view.banner.WrapBannerView;
import com.crazysunj.crazydaily.weex.WeexActivity;
import com.crazysunj.data.util.LoggerUtil;
import com.crazysunj.domain.entity.gankio.GankioEntity;
import com.crazysunj.domain.entity.gaoxiao.GaoxiaoItemEntity;
import com.crazysunj.domain.entity.weather.WeatherXinZhiEntity;
import com.crazysunj.domain.entity.zhihu.ZhihuNewsEntity;
import com.crazysunj.domain.util.JsonUtil;
import com.crazysunj.domain.util.ThreadManager;
import com.google.android.material.appbar.AppBarLayout;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import com.sunjian.android_pickview_lib.BaseOptionsPickerDialog;
import com.sunjian.android_pickview_lib.PhoneOptionsPickerDialog;
import com.xiao.nicevideoplayer.NiceVideoPlayerManager;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import butterknife.BindView;
import permissions.dispatcher.NeedsPermission;
import permissions.dispatcher.OnNeverAskAgain;
import permissions.dispatcher.OnPermissionDenied;
import permissions.dispatcher.OnShowRationale;
import permissions.dispatcher.PermissionRequest;
import permissions.dispatcher.RuntimePermissions;

/**
 * @author: sunjian
 * created on: 2018/11/7 下午10:37
 * description: https://github.com/crazysunj/CrazyDaily
 */
@RuntimePermissions
public class HomeFragment extends BaseFragment<NewHomePresenter> implements NewHomeContract.View, PermissionCamera, PermissionStorage {

    @BindView(R.id.home_refresh)
    SwipeRefreshLayout mRefresh;
    @BindView(R.id.home_list)
    RecyclerView mHomeList;
    @BindView(R.id.home_vp)
    CardViewPager mHomeBanner;
    @BindView(R.id.wrap_banner)
    WrapBannerView mWrapBanner;
    @BindView(R.id.home_toolbar)
    Toolbar mToolbar;
    @BindView(R.id.home_appbar)
    AppBarLayout mAppbar;
    @BindView(R.id.home_title)
    TextView mTitle;

    @Inject
    HomeAdapter mAdapter;

    private PhoneOptionsPickerDialog mGankioDialog;
    private PhoneOptionsPickerDialog mWeatherDialog;
    private ArrayList<CityEntity> mCityList;
    private ArgbEvaluator mArgbEvaluator = new ArgbEvaluator();
    private int gaoxiaoIndex = 1;
    private Drawable mNavigationIcon;

    @Override
    public void onResume() {
        super.onResume();
        mPresenter.startBanner();
    }

    @Override
    public void onPause() {
        super.onPause();
        mPresenter.endBanner();
    }

    @Override
    public void onStop() {
        super.onStop();
        NiceVideoPlayerManager.instance().releaseNiceVideoPlayer();
    }

    @Override
    public void onDestroy() {
        ThreadManager.shutdown();
        super.onDestroy();
    }

    @Override
    protected void initView() {
        setSupportActionBar(mToolbar);
        ActionBar actionBar = mActivity.getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(false);
        }
        mToolbar.setNavigationIcon(R.mipmap.ic_scan);
        mNavigationIcon = mToolbar.getNavigationIcon();
        mHomeList.setLayoutManager(new LinearLayoutManager(mActivity));
        mHomeList.setAdapter(mAdapter);
        mWrapBanner.setOnBannerSlideListener(this::handleWrapBanner);
    }

    @Override
    protected void initListener() {
        mRefresh.setOnRefreshListener(this::onRefresh);
        mAdapter.setOnHeaderClickListener(this::handleHeaderOptions);
        mAppbar.addOnOffsetChangedListener(this::handleAppbarOffsetChangedListener);
        mToolbar.setNavigationOnClickListener(v -> HomeFragmentPermissionsDispatcher.openQRCodeWithPermissionCheck(this));
        mAdapter.setDownloadCallback(url -> HomeFragmentPermissionsDispatcher.downloadWithPermissionCheck(this, url));
    }

    @Override
    protected void initData() {
        mPresenter.getZhihuNewsList();
        mPresenter.getGankioList(GankioEntity.ResultsEntity.PARAMS_ANDROID);
        mPresenter.getWeather("杭州");
        mPresenter.getGaoxiaoList(gaoxiaoIndex);
    }

    private void handleWrapBanner(boolean isCanSlide) {
        if (isCanSlide) {
            mPresenter.startBanner();
        } else {
            mPresenter.endBanner();
        }
    }

    private void onRefresh() {
        mPresenter.endBanner();
        NiceVideoPlayerManager.instance().releaseNiceVideoPlayer();
        initData();
    }

    private void handleAppbarOffsetChangedListener(AppBarLayout appBarLayout, int verticalOffset) {
        final int totalScrollRange = appBarLayout.getTotalScrollRange();
        final float percent = Math.abs(verticalOffset * 1.0f / totalScrollRange);
        mRefresh.setEnabled(verticalOffset == 0);
        final int color = getColor(R.color.colorPrimary);
        mTitle.setTextColor((int) mArgbEvaluator.evaluate(percent, color, Color.WHITE));
        if (mNavigationIcon != null) {
            mNavigationIcon.setColorFilter((int) mArgbEvaluator.evaluate(percent, color, Color.WHITE), PorterDuff.Mode.SRC_IN);
        }
    }

    private void handleHeaderOptions(int level, String options) {
        switch (level) {
            case HomeAdapterHelper.LEVEL_GANK_IO:
                if (mGankioDialog == null) {
                    Bundle bundle = new Bundle();
                    bundle.putBoolean(BaseOptionsPickerDialog.CYCLIC_FIRST, true);
                    ArrayList<String> data = new ArrayList<String>();
                    data.add(GankioEntity.ResultsEntity.PARAMS_ANDROID);
                    data.add(GankioEntity.ResultsEntity.PARAMS_IOS);
                    data.add(GankioEntity.ResultsEntity.PARAMS_H5);
                    data.add(GankioEntity.ResultsEntity.PARAMS_ALL);
                    mGankioDialog = PhoneOptionsPickerDialog.newInstance(bundle, data);
                    mGankioDialog.setOnoptionsSelectListener((options1, option2, options3) -> {
                        final String selectOption = data.get(options1);
                        if (selectOption.equals(options)) {
                            return;
                        }
                        if (GankioEntity.ResultsEntity.PARAMS_ALL.equals(selectOption)) {
                            WeexActivity.start(mActivity, WeexConstant.PAGE_NAME_GANK_IO, WeexConstant.PATH_GANK_IO);
                            return;
                        }
                        mPresenter.getGankioList(selectOption);
                    });
                }
                mGankioDialog.show(mActivity.getFragmentManager(), "GankioDialog");
                break;

            case HomeAdapterHelper.LEVEL_WEATHER:
                if (mCityList == null) {
                    String json = JsonUtil.readLocalJson(mActivity, CityEntity.FILE_NAME);
                    if (TextUtils.isEmpty(json)) {
                        LoggerUtil.d("城市Json数据读取失败！");
                        return;
                    }
                    mCityList = JsonUtil.fromJsonList(json, CityEntity.class);
                }
                if (mWeatherDialog == null) {
                    Bundle bundle = new Bundle();
                    bundle.putBoolean(BaseOptionsPickerDialog.CYCLIC_FIRST, true);
                    mWeatherDialog = PhoneOptionsPickerDialog.newInstance(bundle, mCityList);
                    mWeatherDialog.setOnoptionsSelectListener((options1, option2, options3) -> {
                        final String selectOption = mCityList.get(options1).getCityName();
                        if (selectOption.equals(options)) {
                            return;
                        }
                        mPresenter.getWeather(selectOption);
                    });
                }
                mWeatherDialog.show(mActivity.getFragmentManager(), "WeatherDialog");
                break;
            case HomeAdapterHelper.LEVEL_ZHIHU:
                SnackbarUtil.show(mActivity, "已经最新了，别点了！");
                break;
            case HomeAdapterHelper.LEVEL_GAOXIAO:
                NiceVideoPlayerManager.instance().releaseNiceVideoPlayer();
                mPresenter.getGaoxiaoList(++gaoxiaoIndex);
                break;
            default:
                break;
        }
    }

    private void stopRefresh() {
        if (mRefresh.isRefreshing()) {
            mRefresh.setRefreshing(false);
        }
    }

    @Override
    public void showZhihu(ZhihuNewsEntity zhihuNewsEntity) {
        stopRefresh();
        List<ZhihuNewsEntity.StoriesEntity> stories = zhihuNewsEntity.getStories();
        if (stories != null && !stories.isEmpty()) {
            mAdapter.notifyZhihuNewsList(stories);
        }

        List<ZhihuNewsEntity.TopStoriesEntity> topStories = zhihuNewsEntity.getTop_stories();
        if (topStories == null || topStories.isEmpty()) {
            mHomeBanner.setVisibility(View.GONE);
        } else {
            mHomeBanner.setVisibility(View.VISIBLE);
            mHomeBanner.bind(mActivity.getSupportFragmentManager(), new BannerCardHandler(), topStories);
        }
    }

    @Override
    public void showGankio(List<GankioEntity.ResultsEntity> gankioList) {
        stopRefresh();
        mAdapter.notifyGankioList(gankioList);
    }

    @Override
    public void showWeather(WeatherXinZhiEntity.FinalEntity weatherEntity) {
        stopRefresh();
        mAdapter.notifyWeatherEntity(weatherEntity);
    }

    @Override
    public void showGaoxiao(List<GaoxiaoItemEntity> gaoxiaoList) {
        stopRefresh();
        mAdapter.notifyGaoxiaoList(gaoxiaoList);
    }

    @Override
    public void switchBanner() {
        mHomeBanner.setCurrentItem(mHomeBanner.getCurrentItem() + 1, true);
    }

    @Override
    public void showError(String msg) {
        stopRefresh();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(resultCode, data);
        String scanResult = result.getContents();
        if (scanResult != null) {
            BrowserActivity.start(mActivity, scanResult);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        HomeFragmentPermissionsDispatcher.onRequestPermissionsResult(this, requestCode, grantResults);
    }

    @NeedsPermission({Manifest.permission.CAMERA})
    void openQRCode() {
        IntentIntegrator.forSupportFragment(this)
                .setCaptureActivity(ScannerActivity.class).initiateScan();
    }

    @OnShowRationale({Manifest.permission.CAMERA})
    @Override
    public void showRationaleForCamera(PermissionRequest request) {
        PermissionHelper.cameraShowRationale(mActivity, request);
    }

    @OnPermissionDenied({Manifest.permission.CAMERA})
    @Override
    public void showDeniedForCamera() {
        PermissionHelper.cameraPermissionDenied(mActivity);
    }

    @OnNeverAskAgain({Manifest.permission.CAMERA})
    @Override
    public void showNeverAskForCamera() {
        PermissionHelper.cameraNeverAskAgain(mActivity);
    }

    @NeedsPermission({Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE})
    void download(String url) {
        DownloadService.start(mActivity, url);
    }

    @OnShowRationale({Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE})
    @Override
    public void showRationaleForStorage(PermissionRequest request) {
        PermissionHelper.storageShowRationale(mActivity, request);
    }

    @OnPermissionDenied({Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE})
    @Override
    public void showDeniedForStorage() {
        PermissionHelper.storagePermissionDenied(mActivity);
    }

    @OnNeverAskAgain({Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE})
    @Override
    public void showNeverAskForStorage() {
        PermissionHelper.storageNeverAskAgain(mActivity);
    }

    @Override
    protected void initInject() {
        getFragmentComponent(new EntityModule())
                .inject(this);
    }

    @Override
    protected int getContentResId() {
        return R.layout.fragment_home;
    }
}
