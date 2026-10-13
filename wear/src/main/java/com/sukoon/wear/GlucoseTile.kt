package com.sukoon.wear

import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders.Box
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.FONT_WEIGHT_BOLD
import androidx.wear.protolayout.LayoutElementBuilders.FontStyle
import androidx.wear.protolayout.LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.LayoutElementBuilders.Text
import androidx.wear.protolayout.ModifiersBuilders.Modifiers
import androidx.wear.protolayout.ModifiersBuilders.Semantics
import androidx.wear.protolayout.ResourceBuilders.Resources
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture

/**
 * The glucose tile: the number big in its state colour, the arrow, and how old it is. Grey once the
 * reading is older than 10 minutes, so an old number never passes for a current one.
 */
class GlucoseTile : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<Tile> {
        val tile = Tile.Builder()
            .setResourcesVersion(RESOURCES)
            // ponytail: redrawn each minute for the "min ago"; a DynamicInstant text would tick on its own if this costs battery.
            .setFreshnessIntervalMillis(60_000)
            .setTileTimeline(Timeline.fromLayoutElement(layout(Latest.read(this), System.currentTimeMillis())))
            .build()
        return done(tile)
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<Resources> =
        done(Resources.Builder().setVersion(RESOURCES).build())

    private fun layout(latest: Latest?, now: Long): LayoutElement {
        val column = Column.Builder().setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
        if (latest == null) {
            column.addContent(text(getString(R.string.no_readings), 16f, Latest.STALE))
        } else {
            val color = latest.color(now)
            val minutes = latest.minutesAgo(now)
            val ago = if (minutes == 0) getString(R.string.now) else resources.getQuantityString(R.plurals.minutes_ago, minutes, minutes)
            column
                .addContent(text("${latest.mgDl} ${latest.arrow}", 52f, color, bold = true))
                .addContent(text(getString(R.string.mg_dl), 14f, color))
                .addContent(text(ago, 14f, Latest.STALE))
                .setModifiers(
                    Modifiers.Builder()
                        .setSemantics(Semantics.Builder().setContentDescription(getString(R.string.glucose_description, latest.mgDl) + ", " + ago).build())
                        .build(),
                )
        }
        return Box.Builder().setWidth(expand()).setHeight(expand()).addContent(column.build()).build()
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false): Text {
        val style = FontStyle.Builder().setSize(sp(size)).setColor(argb(color))
        if (bold) style.setWeight(FONT_WEIGHT_BOLD)
        return Text.Builder().setText(value).setFontStyle(style.build()).build()
    }

    private fun <T : Any> done(value: T): ListenableFuture<T> = CallbackToFutureAdapter.getFuture { it.set(value); "tile" }

    private companion object {
        const val RESOURCES = "1"
    }
}
