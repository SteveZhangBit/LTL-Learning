package cmu.s3d.ltl

data class Fluent(
    val name: String,
    val trigger: List<String>,
    val reset: List<String>,
    val init: Boolean
)

fun String.toFluent(): Fluent? {
    val identReg = "\\w+(?:\\.\\w+)*".toRegex()
    val eventReg = "(?:$identReg|\\{\\s*\\w+(?:\\s*,\\s*\\w+)*\\s*\\}\\.$identReg)".toRegex()
    val fluentTriggerReg = "$eventReg|\\{\\s*$eventReg(?:\\s*,\\s*$identReg)*\\s*\\}".toRegex()
    val fluentReg =
        Regex("fluent\\s+(\\w+)\\s*=\\s*<\\s*($fluentTriggerReg)\\s*,\\s*($fluentTriggerReg)\\s*>(?:\\s+initially\\s+([0|1]))?")

    fun getEvents(eventsStr: String): List<String> {
        return eventReg.findAll(eventsStr).flatMap {
            if (it.value[0] == '{') {
                val i = it.value.indexOf('}')
                val prefixes = it.value.substring(1, i).split(",").map(String::trim)
                val suffix = it.value.substring(i + 1)
                prefixes.map { prefix -> prefix + suffix }
            } else {
                listOf(it.value)
            }
        }.toList()
    }

    return fluentReg.matchEntire(this)?.let { match ->
        val (name, trigger, reset, init) = match.destructured
        Fluent(
            name,
            getEvents(trigger),
            getEvents(reset),
            init == "1"
        )
    }
}

fun evaluateFluent(word: Word, fluents: List<Fluent>): LassoTrace {
    val evaluation = mutableListOf<State>()

    // initial state
    evaluation.add(State(fluents.associate { it.name to it.init }))
    // for each input in word
    for (i in word.indices) {
        val input = word[i]
        // evaluate each fluent
        val newEvaluation = evaluation.last().values.toMutableMap()
        for (fluent in fluents) {
            // if fluent is triggered, then set to true
            if (fluent.trigger.contains(input)) {
                newEvaluation[fluent.name] = true
            }
            // if fluent is reset, then set to false
            if (fluent.reset.contains(input)) {
                newEvaluation[fluent.name] = false
            }
        }
        evaluation.add(State(newEvaluation))
    }

    return LassoTrace(prefix = evaluation)
}