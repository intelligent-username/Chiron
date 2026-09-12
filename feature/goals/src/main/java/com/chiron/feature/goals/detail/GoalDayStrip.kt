package com.chiron.feature.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chiron.core.ui.theme.CoolGray
import com.chiron.core.ui.theme.MonospaceFamily
import com.chiron.core.ui.theme.SolidSlate
import com.chiron.core.ui.theme.ThinOutline
import java.time.LocalDate

@Composable
fun GoalDayStrip(
    weekStart: LocalDate,
    dayStatus: Map<LocalDate, Boolean>,
    modifier: Modifier = Modifier
) {
    val dayLetters = listOf("S", "M", "T", "W", "T", "F", "S")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SolidSlate)
            .border(1.dp, ThinOutline, RoundedCornerShape(12.dp))
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        dayLetters.forEachIndexed { index, letter ->
            val date = weekStart.plusDays(index.toLong())
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = letter,
                    color = CoolGray,
                    fontSize = 11.sp,
                    fontFamily = MonospaceFamily
                )
                Spacer(modifier = Modifier.height(6.dp))
                DayDot(
                    done = dayStatus[date] == true,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
