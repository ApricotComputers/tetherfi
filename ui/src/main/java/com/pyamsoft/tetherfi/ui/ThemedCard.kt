/*
 * Copyright 2023 pyamsoft
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.pyamsoft.tetherfi.ui

import androidx.compose.foundation.border
import androidx.compose.material.Card
import androidx.compose.material.ContentAlpha
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.pyamsoft.pydroid.ui.defaults.CardDefaults
import com.pyamsoft.tetherfi.ui.checkable.rememberCheckableColor

@Composable
fun ThemedCard(
    modifier: Modifier = Modifier,
    label: String,
    isChecked: Boolean,
    isEditable: Boolean,
    shape: Shape = MaterialTheme.shapes.medium,
    content: @Composable () -> Unit,
) {
  val cardColor by rememberCheckableColor(label, isChecked, MaterialTheme.colors.primary)
  val mediumAlpha = if (isEditable) ContentAlpha.medium else ContentAlpha.disabled

  Card(
      modifier =
          modifier.border(
              width = 2.dp,
              color = cardColor.copy(alpha = mediumAlpha),
              shape = MaterialTheme.shapes.medium,
          ),
      shape = shape,
      elevation = CardDefaults.Elevation,
      content = content,
  )
}
