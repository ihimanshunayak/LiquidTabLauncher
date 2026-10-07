/*
 * Copyright (c) A|iens. All rights reserved.
 *
 * Liquid Tab Launcher — calendar.
 *
 * Name      : CalendarRepository.kt
 * Version   : 1.0.0
 * Purpose   : Today's events for the calendar widget, read from the platform's
 *             calendar provider.
 *
 * Notes     : Reads only the columns the widget draws, and only for today, so
 *             the query stays a single indexed range rather than a scan of
 *             every event the user has. Returns an empty list when permission
 *             has not been granted — the widget offers the grant in place
 *             rather than a launcher demanding it at first launch, which is the
 *             behaviour that gets permission requests denied.
 *
 *             Instances that repeat are expanded by the provider, so a weekly
 *             stand-up appears without the launcher implementing recurrence.
 */

package com.ihimanshunayak.liquidtab.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One event, as the widget needs it. */
data class CalendarEvent(
    val id: Long,
    val title: String,
    val startMs: Long,
    val endMs: Long,
    val allDay: Boolean,
    val location: String?,
    val colorArgb: Int?,
) {
    /** The event's start as a local time, for the row's leading label. */
    fun startTime(zone: ZoneId): java.time.LocalTime =
        Instant.ofEpochMilli(startMs).atZone(zone).toLocalTime()
}

object CalendarRepository {

    private const val TAG = "CalendarRepository"

    /**
     * The permissions the widget needs. Declared so the UI can request exactly
     * these and nothing wider.
     */
    val PERMISSIONS = arrayOf(Manifest.permission.READ_CALENDAR)

    /** The single permission, for callers that only read the state. */
    const val PERMISSION = Manifest.permission.READ_CALENDAR

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, PERMISSION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Today's events in start order. [day] defaults to today in the device's own
     * zone, which is the only definition of "today" a Home screen should use.
     */
    fun eventsToday(
        context: Context,
        day: LocalDate = LocalDate.now(),
        limit: Int = 12,
    ): List<CalendarEvent> {
        if (!hasPermission(context)) return emptyList()

        val zone = ZoneId.systemDefault()
        val startMs = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMs = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.EVENT_COLOR,
        )

        val events = ArrayList<CalendarEvent>(limit)
        return try {
            // The Instances table is the provider's own view of an event on a
            // given day, which is what makes recurrence free here.
            val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
            ContentUris.appendId(builder, startMs)
            ContentUris.appendId(builder, endMs)

            context.contentResolver.query(
                builder.build(),
                projection,
                null,
                null,
                "${CalendarContract.Instances.BEGIN} ASC",
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID)
                val titleIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
                val beginIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
                val endIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.END)
                val allDayIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY)
                val locationIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_LOCATION)
                val colorIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_COLOR)

                while (cursor.moveToNext() && events.size < limit) {
                    val title = cursor.getString(titleIndex)?.takeIf { it.isNotBlank() }
                    events += CalendarEvent(
                        id = cursor.getLong(idIndex),
                        // A private event with no title still occupies the day;
                        // labeling it beats dropping it silently.
                        title = title ?: "(No title)",
                        startMs = cursor.getLong(beginIndex),
                        endMs = cursor.getLong(endIndex),
                        allDay = cursor.getInt(allDayIndex) == 1,
                        location = cursor.getString(locationIndex),
                        colorArgb = cursor.getInt(colorIndex).takeIf { it != 0 },
                    )
                }
            }
            events
        } catch (t: Throwable) {
            // A revoked permission or a provider that went away mid-query is a
            // normal outcome on a device that is being used; the widget shows
            // its empty state.
            Log.w(TAG, "Calendar query failed", t)
            emptyList()
        }
    }
}
