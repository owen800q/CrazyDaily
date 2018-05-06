/**
 * Copyright 2017 Sun Jian
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.crazysunj.domain.interactor.splash;

import com.crazysunj.domain.interactor.UseCase;

import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import io.reactivex.Flowable;
import io.reactivex.android.schedulers.AndroidSchedulers;

/**
 * author: sunjian
 * created on: 2017/9/5 下午5:34
 * description: https://github.com/crazysunj/CrazyDaily
 */

public class SplashUseCase extends UseCase<Long, SplashUseCase.Params> {


    @Inject
    public SplashUseCase() {
    }

    @Override
    protected Flowable<Long> buildUseCaseObservable(Params params) {
        return Flowable.timer(params.delay, params.unit, AndroidSchedulers.mainThread());
    }

    public static final class Params {

        private final long delay;
        private final TimeUnit unit;

        private Params(long delay, TimeUnit unit) {
            this.delay = delay;
            this.unit = unit;
        }

        public static Params get(long delay, TimeUnit unit) {
            return new Params(delay, unit);
        }
    }
}
