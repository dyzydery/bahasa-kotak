package io.github.dyzydery.bahasakotak.data

import io.github.dyzydery.bahasakotak.domain.CardKey
import io.github.dyzydery.bahasakotak.domain.CardState
import io.github.dyzydery.bahasakotak.domain.StatAnswer
import io.github.dyzydery.bahasakotak.domain.StatTopic
import io.github.dyzydery.bahasakotak.domain.Stats
import io.github.dyzydery.bahasakotak.domain.StatsCalculator
import io.github.dyzydery.bahasakotak.domain.StudyItem
import java.time.Instant
import java.time.ZoneId

class StatsRepository(private val db: BahasaDatabase) {
    suspend fun stats(now: Instant, zone: ZoneId): Stats {
        val data = db.exportDao().exportData()
        val activeLessons = data.lessons.filter { !it.retired }
        return StatsCalculator.compute(
            answers = data.answers.map {
                StatAnswer(it.itemId, it.direction, it.exerciseType, it.grade, it.answeredAt, it.durationMs, it.boxAfter, it.retry)
            },
            cards = data.cards.map {
                CardState(CardKey(it.itemId, it.direction), it.box, it.dueAt ?: 0, it.masteredIntervalDays, it.introducedAt ?: 0)
            },
            items = data.items.filter { !it.retired }.associate {
                it.id to StudyItem(it.id, it.lessonId, it.kind, it.idText, it.plText, it.register, placementOnly = it.placementOnly)
            },
            lessonTopic = activeLessons.associate { it.id to it.topicId },
            topics = data.topics.filter { !it.retired }.map { StatTopic(it.id, it.title, it.order) },
            now = now,
            zone = zone,
        )
    }
}
