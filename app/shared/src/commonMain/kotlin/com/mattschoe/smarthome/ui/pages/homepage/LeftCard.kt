package com.mattschoe.smarthome.ui.pages.homepage

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mattschoe.smarthome.data.ClockText
import com.mattschoe.smarthome.data.danishMonths
import com.mattschoe.smarthome.data.danishWeekdays
import com.mattschoe.smarthome.data.formatClock
import com.mattschoe.smarthome.data.NO_VALUE
import com.mattschoe.smarthome.data.formatEnergy
import com.mattschoe.smarthome.data.formatHumidity
import com.mattschoe.smarthome.data.formatTemp
import com.mattschoe.smarthome.data.model.ClimateState
import com.mattschoe.smarthome.ui.components.CardContainer
import com.mattschoe.smarthome.ui.components.SectionLabel
import com.mattschoe.smarthome.ui.theme.Card
import com.mattschoe.smarthome.ui.theme.CardBorder
import com.mattschoe.smarthome.ui.theme.Dimensions
import com.mattschoe.smarthome.ui.theme.Ink
import com.mattschoe.smarthome.ui.theme.InkSoft
import com.mattschoe.smarthome.ui.theme.Rose
import com.mattschoe.smarthome.ui.theme.SageGreen
import com.mattschoe.smarthome.ui.theme.Teal
import com.mattschoe.smarthome.ui.theme.WarmAmber
import kotlinx.coroutines.delay
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import smarthome.shared.generated.resources.Res
import smarthome.shared.generated.resources.energy_filled
import smarthome.shared.generated.resources.humidity_filled
import smarthome.shared.generated.resources.sun_filled
import smarthome.shared.generated.resources.thermometer_filled

/**
 * The fixed left column: date/time header, a 2×2 read-only climate glance, and the (currently empty)
 * titled Apps card. Width-agnostic — it fills whatever width the caller assigns (the `Expanded`
 * branch gives it [Dimensions.leftCardWidth]); all page geometry lives at the assembly point, not here.
 */
@Composable
fun LeftCard(
    climate: ClimateState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Dimensions.cardGap),
    ) {
        DateTimeHeader()
        ClimateGrid(climate)
        AppsCard(Modifier.weight(1f))
    }
}

/** Weekday + date on the left, time on the right. Re-formats every 20s. Danish, 24h. */
@Composable
private fun DateTimeHeader() {
    var clock by remember { mutableStateOf(nowClockText()) }
    LaunchedEffectClockTick { clock = nowClockText() }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(clock.weekday, color = Ink, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
            Text(clock.date, color = InkSoft, style = MaterialTheme.typography.headlineSmall)
        }
        Text(clock.time, color = InkSoft, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Medium)
    }
}

private fun nowClockText(): ClockText =
    formatClock(
        instant = Clock.System.now(),
        tz = TimeZone.currentSystemDefault(),
        weekdayNames = danishWeekdays,
        monthNames = danishMonths,
        use24h = true,
    )

/** Ticks [onTick] roughly every 20s while composed (the wall clock does not need second precision). */
@Composable
private fun LaunchedEffectClockTick(onTick: () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            delay(20_000)
            onTick()
        }
    }
}

/** 2×2 grid of read-only climate tiles bound to [climate]. */
@Composable
private fun ClimateGrid(climate: ClimateState) {
    // A missing sensor reads "ukendt" (unknown) to the screen reader, not the "—" glyph.
    fun describe(label: String, value: String) =
        "$label ${if (value == NO_VALUE) "ukendt" else value}"

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ClimateTile(
                icon = Res.drawable.thermometer_filled,
                iconTint = Rose,
                value = formatTemp(climate.indoorTempC),
                contentDescription = describe("Indetemperatur", formatTemp(climate.indoorTempC)),
                modifier = Modifier.weight(1f),
            )
            ClimateTile(
                icon = Res.drawable.humidity_filled,
                iconTint = Teal,
                value = formatHumidity(climate.humidityPct),
                contentDescription = climate.humidityPct?.let { "Luftfugtighed $it procent" }
                    ?: "Luftfugtighed ukendt",
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ClimateTile(
                icon = Res.drawable.energy_filled,
                iconTint = WarmAmber,
                value = formatEnergy(climate.energyKw),
                contentDescription = describe("Energiforbrug", formatEnergy(climate.energyKw)),
                modifier = Modifier.weight(1f),
            )
            ClimateTile(
                icon = Res.drawable.sun_filled,
                iconTint = SageGreen,
                value = formatTemp(climate.outdoorTempC),
                contentDescription = describe("Udetemperatur", formatTemp(climate.outdoorTempC)),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** One climate stat: a cream inner-block tile with a soft drop shadow, a colored icon and a large value. */
@Composable
private fun ClimateTile(
    icon: DrawableResource,
    iconTint: Color,
    value: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(Dimensions.innerBlockRadius)
    Column(
        modifier = modifier
            .shadow(Dimensions.cardElevation, shape)
            .clip(shape)
            .background(Card)
            .border(1.dp, CardBorder, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .clearAndSetSemantics { this.contentDescription = contentDescription },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(26.dp),
        )
        Text(value, color = Ink, fontSize = 32.sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Titled Apps card. App shortcuts are deferred until after the dashboard is complete; this reserves
 * the layout slot with an empty flexible region that will become the 2-column scroll grid later.
 */
@Composable
private fun AppsCard(modifier: Modifier = Modifier) {
    CardContainer(
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            SectionLabel("Apps")
            Spacer(Modifier.weight(1f))
        }
    }
}
