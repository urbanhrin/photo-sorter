package com.hoon.photosorter

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder

class App : Application(), ImageLoaderFactory {
    // 동영상 미리보기(첫 장면)도 사진처럼 불러오기 위해 디코더 추가
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(VideoFrameDecoder.Factory()) }
            .build()
}
