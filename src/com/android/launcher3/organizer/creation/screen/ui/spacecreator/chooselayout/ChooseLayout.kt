/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.launcher3.organizer.creation.screen.ui.spacecreator.chooselayout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.carousel.CarouselState
import androidx.compose.material3.carousel.HorizontalUncontainedCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import com.android.launcher3.R
import com.android.launcher3.organizer.creation.screen.ui.spacecreator.SpaceCreatorViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview
fun ChooseLayout(
    @PreviewParameter(CreatorViewModelProvider::class) viewModel: SpaceCreatorViewModel
) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            CenterAlignedTopAppBar(
                modifier = Modifier.padding(ChooseLayoutDimens.contentSidePadding),
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                    ),
                title = {
                    Text(
                        text = stringResource(R.string.organizer_choose_layout_title),
                        color = colorResource(R.color.materialColorOnSurface),
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {},
                        modifier =
                            Modifier.background(
                                ChooseLayoutDimens.AndroidOnlySe1Se1,
                                shape = ChooseLayoutDimens.circleShape,
                            ),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = colorResource(R.color.materialColorOnSurface),
                        )
                    }
                },
            )
        },
        content = { padding -> ChooseLayoutContent(padding = padding, viewModel = viewModel) },
    )
}

@Composable
fun ChooseLayoutContent(padding: PaddingValues, viewModel: SpaceCreatorViewModel) {
    val carouselState = rememberCarouselState { viewModel.chooseLayoutState.layouts.size }
    LaunchedEffect(carouselState) { viewModel.setSelectedLayout(carouselState.currentItem) }
    Column(
        modifier = Modifier.padding(padding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement =
            Arrangement.spacedBy(ChooseLayoutDimens.chooseLayoutContentItemSpacing),
    ) {
        HorizontalUncontainedCarousel(
            state = carouselState,
            itemWidth = ChooseLayoutDimens.itemWidth,
            itemSpacing = ChooseLayoutDimens.carouselItemSpacing,
            contentPadding = ChooseLayoutDimens.carouselPadding,
        ) { i ->
            LayoutPreview()
        }
        PaginationDots(carouselState)
        AddButton()
    }
}

@Composable
fun LayoutPreview() {
    Row(
        Modifier.width(ChooseLayoutDimens.itemWidth)
            .height(ChooseLayoutDimens.itemHeight)
            // TODO(): Remove for real implementation.
            .background(Color.Magenta, shape = RoundedCornerShape(size = 24.dp))
    ) {
        // TODO(): PreviewLayout goes here.
    }
}

@Composable
fun PaginationDots(carouselState: CarouselState) {
    val tint = colorResource(R.color.materialColorPrimary)
    val scope = rememberCoroutineScope()
    Row(
        Modifier.width(ChooseLayoutDimens.paginationDotsWidth)
            .height(ChooseLayoutDimens.paginationDotsHeight)
            .padding(all = 0.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        IconButton(
            onClick = {
                scope.launch {
                    if (carouselState.canScrollBackward) {
                        val prevIndex = (carouselState.currentItem - 1)
                        carouselState.scrollToItem(prevIndex)
                    }
                }
            }
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Preview layout",
                tint = tint,
            )
        }
        IconButton(
            onClick = {
                scope.launch {
                    if (carouselState.canScrollForward) {
                        val nextIndex = (carouselState.currentItem + 1)
                        carouselState.scrollToItem(nextIndex)
                    }
                }
            }
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Next layout",
                tint = tint,
            )
        }
    }
}

@Composable
fun AddButton() {
    Button(
        onClick = {},
        modifier =
            Modifier.wrapContentSize()
                .background(
                    color = colorResource(R.color.materialColorPrimary),
                    shape = ChooseLayoutDimens.addButtonShape,
                )
                .width(ChooseLayoutDimens.addButtonWidth)
                .height(ChooseLayoutDimens.addButtonHeight),
    ) {
        Text("Add", style = TextStyle(color = colorResource(R.color.materialColorOnSurface)))
    }
}

/** Used for development to preview ChooseLayout. */
class CreatorViewModelProvider : PreviewParameterProvider<SpaceCreatorViewModel> {
    val viewModel = SpaceCreatorViewModel()

    init {
        viewModel.updateTopics(
            listOf("Most", "Games", "Health", "Productivity", "Travel", "Social", "Entertainment")
        )
        viewModel.updateLayouts(5)
    }

    override val values: Sequence<SpaceCreatorViewModel>
        get() = listOf(viewModel).asSequence()
}

object ChooseLayoutDimens {
    val chooseLayoutContentItemSpacing = 26.dp
    val iconSize = 41.dp
    val itemWidth = 312.00003.dp
    val itemHeight = 499.04849.dp
    val carouselPadding = PaddingValues(start = 16.dp, end = 16.dp)
    val carouselItemSpacing = 16.dp
    val contentSidePadding = 24.dp
    val AndroidOnlySe1Se1: Color = Color(0x8A081034)
    val textColor = Color.White
    val addButtonWidth = 80.dp
    val addButtonHeight = 56.dp
    val addButtonShape = RoundedCornerShape(size = 100.dp)
    val paginationDotsWidth = 140.dp
    val paginationDotsHeight = 48.dp
    val circleShape = RoundedCornerShape(size = 360.dp)
}
