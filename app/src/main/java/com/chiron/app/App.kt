package com.chiron.app

import android.app.Application
import coil.ImageLoaderFactory
import coil.ImageLoader
import coil.decode.SvgDecoder
import com.chiron.app.di.ServiceLocator
import com.chiron.app.ui.components.prefetchAllIcons

class App : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        // Warm up Coil's memory cache for all exercise icons at startup,
        // so the first visit to the Exercises tab is instant.
        prefetchAllIcons(this)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(SvgDecoder.Factory())
            }
            .build()
    }
}
