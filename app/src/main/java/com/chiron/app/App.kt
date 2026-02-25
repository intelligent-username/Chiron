package com.chiron.app

import android.app.Application
import coil.ImageLoaderFactory
import coil.ImageLoader
import coil.decode.SvgDecoder
import com.chiron.app.di.ServiceLocator

class App : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(SvgDecoder.Factory())
            }
            .build()
    }
}


