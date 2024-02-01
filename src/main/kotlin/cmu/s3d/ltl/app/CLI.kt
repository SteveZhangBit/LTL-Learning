package cmu.s3d.ltl.app

import cmu.s3d.ltl.learning.LTLLearner
import cmu.s3d.ltl.samples2ltl.TaskParser
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import edu.mit.csail.sdg.translator.A4Options
import java.io.File
import java.util.*

class CLI : CliktCommand(
    name = "LTL-Learning",
    help = "A tool to learn LTL formulas from a set of positive and negative examples by using AlloyMax."
) {
    private val solver by option("--solver", "-s", help = "The AlloyMax solver to use.").default("SAT4JMax")
    private val filename by option("--filename", "-f", help = "The file containing one task to run.")
    private val traces by option("--traces", "-t", help = "The folder containing the tasks to run.")
    private val timeout by option("--timeout", "-T", help = "The timeout in seconds for solving each task.").default("5")

    override fun run() {
        val options = LTLLearner.defaultAlloyOptions()
        options.solver = when (solver) {
            "SAT4JMax" -> A4Options.SatSolver.SAT4JMax
            "OpenWBO" -> A4Options.SatSolver.OpenWBO
            "OpenWBOWeighted" -> A4Options.SatSolver.OpenWBOWeighted
            "POpenWBO" -> A4Options.SatSolver.POpenWBO
            "POpenWBOAuto" -> A4Options.SatSolver.POpenWBOAuto
            else -> throw IllegalArgumentException("Unknown solver: $solver")
        }

        if (filename == null && traces == null) {
            println("Please provide a filename or a folder with traces.")
            return
        }

        if (filename != null) {
            val f = File(filename!!)
            if (f.isFile && f.name.endsWith(".trace")) {
                println("--- solving ${f.name}")
                val task = TaskParser.parseTask(f.readText())
                val learner = task.buildLearner(options)
                val startTime = System.currentTimeMillis()
                val solution = learner.learn()
                val solvingTime = (System.currentTimeMillis() - startTime).toDouble() / 1000
                val formula = solution?.getLTL2() ?: "-"
                println("${f.name},${task.toCSVString()},$solvingTime,$formula")
            }
        } else if (traces != null) {
            val folder = File(traces!!)
            if (folder.isDirectory) {
                folder.walk()
                    .filter { it.isFile && it.name.endsWith(".trace") }
                    .forEach {
                        val cmd = listOf(
                            "java",
                            "-Djava.library.path=${System.getProperty("java.library.path")}",
                            "-cp",
                            System.getProperty("java.class.path"),
                            "cmu.s3d.ltl.app.CLIKt",
                            "-f",
                            it.absolutePath,
                            "-s",
                            solver,
                            "-T",
                            timeout
                        )
                        val processBuilder = ProcessBuilder(cmd)

                        val process = processBuilder.start()
                        val timer = Timer(true)
                        timer.schedule(object : TimerTask() {
                            override fun run() {
                                process.destroy()
                            }
                        }, timeout.toLong() * 1000)
                        val output = process.inputStream.bufferedReader().readText()
                        println(output)

                        process.waitFor()
                        timer.cancel()
                    }
            }
        }
    }
}

fun main(args: Array<String>) {
    CLI().main(args)
}