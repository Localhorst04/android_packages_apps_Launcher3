/*
 * Copyright (C) 2025-2026 AxionOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3.folder

import android.graphics.RectF
import com.android.launcher3.model.data.ItemInfo
import kotlin.math.roundToInt

object FolderPreviewLayout {

    data class ContentSelection(val directItems: List<ItemInfo>, val overviewItems: List<ItemInfo>)

    enum class ItemRole {
        DIRECT,
        OVERVIEW,
    }

    data class ItemPlacement(val item: ItemInfo, val role: ItemRole, val bounds: RectF)

    data class Snapshot(
        val backgroundBounds: RectF,
        val overviewBounds: RectF?,
        val items: List<ItemPlacement>,
    )

    private fun squareBounds(left: Float, top: Float, size: Float) =
        RectF(left, top, left + size, top + size)

    @JvmStatic
    fun selectItems(items: List<ItemInfo>, spanX: Int, spanY: Int): ContentSelection {
        require(spanX > 0 && spanY > 0)

        val capacity = spanX * spanY

        if (items.size <= capacity) {
            return ContentSelection(directItems = items.toList(), overviewItems = emptyList())
        }

        val directItemCount = capacity - 1

        return ContentSelection(
            directItems = items.take(directItemCount),
            overviewItems =
                items
                    .drop(directItemCount)
                    .take(ClippedFolderIconLayoutRule.MAX_NUM_ITEMS_IN_PREVIEW),
        )
    }

    @JvmStatic
    fun calculateTileBounds(
        cellIndex: Int,
        spanX: Int,
        spanY: Int,
        backgroundBounds: RectF,
        tileSize: Float,
        isRtl: Boolean,
    ): RectF {
        require(spanX > 0 && spanY > 0)
        require(cellIndex in 0 until spanX * spanY)
        require(tileSize > 0f)
        require(backgroundBounds.width() >= tileSize)
        require(backgroundBounds.height() >= tileSize)

        val row = cellIndex / spanX
        val logicalColumn = cellIndex % spanX
        val column = if (isRtl) spanX - logicalColumn - 1 else logicalColumn

        val horizontalStep =
            if (spanX == 1) 0f else (backgroundBounds.width() - tileSize) / (spanX - 1)
        val verticalStep =
            if (spanY == 1) 0f else (backgroundBounds.height() - tileSize) / (spanY - 1)

        val left = backgroundBounds.left + column * horizontalStep
        val top = backgroundBounds.top + row * verticalStep

        return squareBounds(left, top, tileSize)
    }

    @JvmStatic
    fun calculateDirectPlacements(
        items: List<ItemInfo>,
        spanX: Int,
        spanY: Int,
        backgroundBounds: RectF,
        tileSize: Float,
        directIconSize: Float,
        isRtl: Boolean,
    ): List<ItemPlacement> {
        require(items.size <= spanX * spanY)
        require(directIconSize > 0f)

        val renderedIconSize = directIconSize.coerceAtMost(tileSize)
        val halfIconSize = renderedIconSize / 2f

        val placements =
            items.mapIndexed { cellIndex, item ->
                val tileBounds =
                    calculateTileBounds(cellIndex, spanX, spanY, backgroundBounds, tileSize, isRtl)

                val left = tileBounds.centerX() - halfIconSize
                val top = tileBounds.centerY() - halfIconSize
                val iconBounds = squareBounds(left, top, renderedIconSize)

                ItemPlacement(item = item, role = ItemRole.DIRECT, bounds = iconBounds)
            }

        return placements
    }

    @JvmStatic
    fun calculateOverviewPlacements(
        items: List<ItemInfo>,
        overviewBounds: RectF,
        tileSize: Float,
        intrinsicIconSize: Float,
        isRtl: Boolean,
        folderColumnCount: Int,
    ): List<ItemPlacement> {
        require(items.size in 2..ClippedFolderIconLayoutRule.MAX_NUM_ITEMS_IN_PREVIEW)
        require(tileSize > 0f)
        require(intrinsicIconSize > 0f)
        require(folderColumnCount > 0)

        val layoutRule = ClippedFolderIconLayoutRule()
        layoutRule.init(tileSize.roundToInt(), intrinsicIconSize, isRtl, folderColumnCount)

        val placements =
            items.mapIndexed { index, item ->
                val params = layoutRule.computePreviewItemDrawingParams(index, items.size, null)
                val renderedIconSize = intrinsicIconSize * params.scale

                val left = overviewBounds.left + params.transX
                val top = overviewBounds.top + params.transY
                val iconBounds = squareBounds(left, top, renderedIconSize)

                ItemPlacement(item = item, role = ItemRole.OVERVIEW, bounds = iconBounds)
            }

        return placements
    }

    @JvmStatic
    fun calculateSnapshot(
        items: List<ItemInfo>,
        spanX: Int,
        spanY: Int,
        backgroundBounds: RectF,
        tileSize: Float,
        directIconSize: Float,
        intrinsicIconSize: Float,
        isRtl: Boolean,
        folderColumnCount: Int,
    ): Snapshot {
        val snapshotBounds = RectF(backgroundBounds)
        val selection = selectItems(items, spanX, spanY)

        val directPlacements =
            calculateDirectPlacements(
                selection.directItems,
                spanX,
                spanY,
                snapshotBounds,
                tileSize,
                directIconSize,
                isRtl,
            )

        if (selection.overviewItems.isEmpty()) {
            return Snapshot(snapshotBounds, null, directPlacements)
        }

        val overviewCellIndex = spanX * spanY - 1
        val overviewBounds =
            calculateTileBounds(overviewCellIndex, spanX, spanY, snapshotBounds, tileSize, isRtl)

        val overviewPlacements =
            calculateOverviewPlacements(
                selection.overviewItems,
                overviewBounds,
                tileSize,
                intrinsicIconSize,
                isRtl,
                folderColumnCount,
            )

        val placements = directPlacements + overviewPlacements
        return Snapshot(snapshotBounds, overviewBounds, placements)
    }
}
