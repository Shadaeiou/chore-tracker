package com.chore.tracker.data

import java.util.Calendar

/**
 * Household search supports plain substrings plus a few special tokens that
 * filter on structured fields. Multi-token queries are AND-ed.
 *
 * Special tokens:
 *  - `<N>d` / `freq:<N>`        → exact frequencyDays match
 *  - `notes` / `has:notes`      → task has a non-blank notes field
 *  - `today` / `yesterday`      → lastDoneAt falls on that day
 *  - `week` / `thisweek`        → lastDoneAt within last 7 days
 *  - `lastweek`                 → lastDoneAt within the 7 days before that
 *  - `month` / `thismonth`      → lastDoneAt within last 30 days
 *  - `never`                    → lastDoneAt is null
 *
 * Any token is allowed to also match as a plain substring against task name,
 * area name, assignee name, or notes content, so a literal search like
 * "kitchen" still works even if the user's area happens to be called "Kitchen".
 */
fun taskMatchesHouseholdSearch(
    task: Task,
    area: Area,
    query: String,
    now: Long = System.currentTimeMillis(),
): Boolean {
    val tokens = tokenize(query)
    if (tokens.isEmpty()) return true
    return tokens.all { token -> tokenMatches(task, area, token, now) }
}

/** Used to keep an empty area visible when the query substring-matches its name. */
fun areaNameMatchesAllTokens(area: Area, query: String): Boolean {
    val tokens = tokenize(query)
    if (tokens.isEmpty()) return true
    val name = area.name.lowercase()
    return tokens.all { name.contains(it) }
}

private fun tokenize(query: String): List<String> =
    query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }

private fun tokenMatches(task: Task, area: Area, token: String, now: Long): Boolean {
    if (substringMatch(task, area, token)) return true
    if (frequencyMatch(task, token)) return true
    if (notesMatch(task, token)) return true
    if (dateMatch(task, token, now)) return true
    return false
}

private fun substringMatch(task: Task, area: Area, token: String): Boolean {
    if (task.name.lowercase().contains(token)) return true
    if (area.name.lowercase().contains(token)) return true
    task.assignedToName?.lowercase()?.let { if (it.contains(token)) return true }
    task.notes?.lowercase()?.let { if (it.contains(token)) return true }
    return false
}

private fun frequencyMatch(task: Task, token: String): Boolean {
    val n = parseFrequencyToken(token) ?: return false
    return task.frequencyDays == n
}

private fun parseFrequencyToken(token: String): Int? {
    if (token.matches(Regex("^\\d+d$"))) return token.dropLast(1).toIntOrNull()
    if (token.startsWith("freq:")) return token.removePrefix("freq:").toIntOrNull()
    return null
}

private fun notesMatch(task: Task, token: String): Boolean {
    if (token != "notes" && token != "has:notes") return false
    return !task.notes.isNullOrBlank()
}

private fun dateMatch(task: Task, token: String, now: Long): Boolean {
    val last = task.lastDoneAt
    if (token == "never") return last == null
    if (last == null) return false
    val range = relativeRange(token, now) ?: return false
    return last in range
}

private const val DAY_MS = 86_400_000L

private fun relativeRange(token: String, now: Long): LongRange? {
    val cal = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val todayStart = cal.timeInMillis
    val todayEnd = todayStart + DAY_MS - 1
    return when (token) {
        "today" -> todayStart..todayEnd
        "yesterday" -> (todayStart - DAY_MS)..(todayStart - 1)
        "week", "thisweek" -> (todayStart - 6 * DAY_MS)..todayEnd
        "lastweek" -> (todayStart - 13 * DAY_MS)..(todayStart - 7 * DAY_MS - 1)
        "month", "thismonth" -> (todayStart - 29 * DAY_MS)..todayEnd
        else -> null
    }
}
