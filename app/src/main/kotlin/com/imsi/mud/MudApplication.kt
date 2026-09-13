package com.imsi.mud

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import com.imsi.mud.image.createPhase1ImageLoader

class MudApplication : Application(), SingletonImageLoader.Factory {
    override fun newImageLoader(context: Context): ImageLoader = createPhase1ImageLoader(context)
}
