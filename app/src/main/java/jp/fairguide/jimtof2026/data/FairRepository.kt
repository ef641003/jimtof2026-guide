package jp.fairguide.jimtof2026.data

import android.content.Context

/** 同梱データを一度だけ読み込んで使い回す */
object FairRepository {
    private const val ASSET_NAME = "jimtof2026.json"

    @Volatile
    private var cached: FairData? = null

    fun get(context: Context): FairData {
        cached?.let { return it }
        return synchronized(this) {
            cached ?: load(context.applicationContext).also { cached = it }
        }
    }

    private fun load(context: Context): FairData =
        context.assets.open(ASSET_NAME).bufferedReader(Charsets.UTF_8).use { reader ->
            FairDataParser.parse(reader.readText())
        }
}
