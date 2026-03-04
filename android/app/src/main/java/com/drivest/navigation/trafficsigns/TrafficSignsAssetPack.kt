package com.drivest.navigation.trafficsigns

import android.content.Context
import com.google.android.play.core.assetpacks.AssetPackManagerFactory
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

object TrafficSignsAssetPack {
    const val PACK_NAME = "traffic_signs"
    private const val PACK_PREFIX = "traffic_signs/"

    fun resolvePath(assetPath: String): String {
        return if (assetPath.startsWith(PACK_PREFIX)) assetPath else "$PACK_PREFIX$assetPath"
    }

    fun packAssetsPath(context: Context): String? {
        return AssetPackManagerFactory.getInstance(context)
            .getPackLocation(PACK_NAME)
            ?.assetsPath()
    }

    fun isPackAvailable(context: Context): Boolean {
        return packAssetsPath(context) != null
    }

    fun openAsset(context: Context, assetPath: String): InputStream {
        val resolvedPath = resolvePath(assetPath)
        val packPath = packAssetsPath(context)
        if (packPath != null) {
            return FileInputStream(File(packPath, resolvedPath))
        }
        return context.assets.open(resolvedPath)
    }
}
