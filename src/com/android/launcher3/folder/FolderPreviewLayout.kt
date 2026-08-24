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

    data class Grid(
        val columns: Int,
        val rows: Int,
        val startX: Float,
        val startY: Float,
        val itemSize: Float,
        val gap: Float,
    ) {
        val capacity: Int
            get() = columns * rows
    }

    data class Snapshot(
        val backgroundBounds: RectF,
        val overviewBounds: RectF?,
        val items: List<ItemPlacement>,
    )

    private fun squareBounds(left: Float, top: Float, size: Float) =
        RectF(left, top, left + size, top + size)

    @JvmStatic
    fun selectItems(items: List<ItemInfo>, capacity: Int): ContentSelection {
        require(capacity > 0)

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
    fun calculateDirectPlacements(
        items: List<ItemInfo>,
        grid: Grid,
        isRtl: Boolean,
    ): List<ItemPlacement> {
        require(items.size <= grid.capacity)

        val placements =
            items.mapIndexed { index, item ->
                ItemPlacement(
                    item = item,
                    role = ItemRole.DIRECT,
                    bounds = calculateGridItemBounds(index, grid, isRtl),
                )
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
    fun calculateGrid(availableBounds: RectF, itemSize: Float, gap: Float): Grid {
        require(itemSize > 0f)
        require(gap >= 0f)

        val availableWidth = availableBounds.width()
        val availableHeight = availableBounds.height()
        require(availableWidth >= itemSize && availableHeight >= itemSize)

        val columns = ((availableWidth + gap) / (itemSize + gap)).toInt()
        val rows = ((availableHeight + gap) / (itemSize + gap)).toInt()

        val usedWidth = columns * itemSize + (columns - 1) * gap
        val usedHeight = rows * itemSize + (rows - 1) * gap

        return Grid(
            columns = columns,
            rows = rows,
            startX = availableBounds.centerX() - usedWidth / 2f,
            startY = availableBounds.centerY() - usedHeight / 2f,
            itemSize = itemSize,
            gap = gap,
        )
    }

    @JvmStatic
    fun calculateGridItemBounds(index: Int, grid: Grid, isRtl: Boolean): RectF {
        require(index in 0 until grid.capacity)

        val row = index / grid.columns
        val logicalColumn = index % grid.columns
        val column = if (isRtl) grid.columns - logicalColumn - 1 else logicalColumn
        val step = grid.itemSize + grid.gap

        return squareBounds(grid.startX + column * step, grid.startY + row * step, grid.itemSize)
    }

    @JvmStatic
    fun isTightlyWrapped(itemCount: Int, grid: Grid): Boolean {
        val occupiedSlots = minOf(itemCount, grid.capacity)

        return occupiedSlots >= grid.columns && occupiedSlots > (grid.rows - 1) * grid.columns
    }

    @JvmStatic
    fun calculateSnapshot(
        items: List<ItemInfo>,
        backgroundBounds: RectF,
        grid: Grid,
        intrinsicIconSize: Float,
        isRtl: Boolean,
        folderColumnCount: Int,
    ): Snapshot {
        val snapshotBounds = RectF(backgroundBounds)
        val selection = selectItems(items, grid.capacity)

        val directPlacements = calculateDirectPlacements(selection.directItems, grid, isRtl)

        if (selection.overviewItems.isEmpty()) {
            return Snapshot(snapshotBounds, null, directPlacements)
        }

        val overviewIndex = grid.capacity - 1
        val overviewBounds = calculateGridItemBounds(overviewIndex, grid, isRtl)

        val overviewPlacements =
            calculateOverviewPlacements(
                selection.overviewItems,
                overviewBounds,
                grid.itemSize,
                intrinsicIconSize,
                isRtl,
                folderColumnCount,
            )

        return Snapshot(snapshotBounds, overviewBounds, directPlacements + overviewPlacements)
    }
}
