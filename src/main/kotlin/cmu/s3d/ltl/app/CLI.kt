package cmu.s3d.ltl.app

import cmu.s3d.ltl.learning.AlloyMaxBase
import cmu.s3d.ltl.samples2ltl.TaskParser
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import edu.mit.csail.sdg.translator.A4Options
import java.io.File
import java.lang.management.ManagementFactory
import java.nio.file.Paths
import java.util.*

class CLI : CliktCommand(
    name = "LTL-Learning",
    help = "A tool to learn LTL formulas from a set of positive and negative examples by using AlloyMax."
) {
    private val _run by option("--_run", help = "Run the learning process. YOU SHOULD NOT USE THIS. INTERNAL USE ONLY.")
    private val solver by option("--solver", "-s", help = "The AlloyMax solver to use. Default: SAT4JMax").default("SAT4JMax")
    private val filename by option("--filename", "-f", help = "The file containing one task to run.")
    private val traces by option("--traces", "-t", help = "The folder containing the tasks to run. It will find all task files under the folder recursively.")
    private val timeout by option("--timeout", "-T", help = "The timeout in seconds for solving each task.").default("5")
    private val model by option("--model", "-m", help = "Print the model to use for learning.").flag(default = false)

    override fun run() {
        val options = AlloyMaxBase.defaultAlloyOptions()
        options.solver = when (solver) {
            "SAT4JMax" -> A4Options.SatSolver.SAT4JMax
            "OpenWBO" -> A4Options.SatSolver.OpenWBO
            "OpenWBOWeighted" -> A4Options.SatSolver.OpenWBOWeighted
            "POpenWBO" -> A4Options.SatSolver.POpenWBO
            "POpenWBOAuto" -> A4Options.SatSolver.POpenWBOAuto
            else -> throw IllegalArgumentException("Unknown solver: $solver")
        }

        if (_run != null) {
            val f = File(_run!!)
            if (f.isFile && f.name.endsWith(".trace")) {
//                println("--- solving ${f.name}")
                val task = TaskParser.parseTask(f.readText())
                try {
                    val learner = task.buildLearner(options)
                    if (model)
                        println(learner.generateAlloyModel().trimIndent())
                    val startTime = System.currentTimeMillis()
                    val solution = learner.learn()
                    val solvingTime = (System.currentTimeMillis() - startTime).toDouble() / 1000
                    val formula = solution?.getLTL2() ?: "UNSAT"
                    println("$_run,${task.toCSVString()},$solvingTime,\"$formula\"")
                } catch (e: Exception) {
                    val message = e.message?.replace("\\v".toRegex(), " ") ?: "Unknown error"
                    println("$_run,${task.toCSVString()},\"ERR:$message\",-")
                }
            }
            return
        }

        if (filename == null && traces == null) {
            println("Please provide a filename or a folder with traces.")
            return
        }

        println("filename,numOfPositives,numOfNegatives,maxDepth,numOfVariables,maxLengthOfTraces,expected,solvingTime,formula")
        if (filename != null) {
            val f = File(filename!!)
            if (f.isFile && f.name.endsWith(".trace")) {
                runInSubProcess(f, filename!!)
            } else {
                error("The file $filename does not exist or is not a .trace file.")
            }
        } else if (traces != null) {
            val folder = File(traces!!)
            if (folder.isDirectory) {
                folder.walk()
                    .filter { it.isFile && it.name.endsWith(".trace") }
                    .forEach { runInSubProcess(it, Paths.get(traces!!, it.absolutePath.substringAfter(traces!!)).toString())  }
            }
        }
    }

    private fun runInSubProcess(f: File, pathName: String) {
        val jvmArgs = ManagementFactory.getRuntimeMXBean().inputArguments
        val jvmXms = jvmArgs.find { it.startsWith("-Xms") }
        val jvmXmx = jvmArgs.find { it.startsWith("-Xmx") }

        val cmd = mutableListOf(
            "java",
            jvmXms ?: "-Xms512m",
            jvmXmx ?: "-Xmx4g",
            "-Djava.library.path=${System.getProperty("java.library.path")}",
            "-cp",
            System.getProperty("java.class.path"),
            "cmu.s3d.ltl.app.CLIKt",
            "--_run",
            pathName,
            "-s",
            solver,
            "-T",
            timeout,
        )
        if (model)
            cmd.add("-m")
        val processBuilder = ProcessBuilder(cmd)
        processBuilder.redirectErrorStream(true)
        val process = processBuilder.start()

        try {
            val timer = Timer(true)
            timer.schedule(object : TimerTask() {
                override fun run() {
                    if (process.isAlive) {
                        process.destroy()

                        val task = TaskParser.parseTask(f.readText())
                        println("$pathName,${task.toCSVString()},TO,-")
                    }
                }
            }, timeout.toLong() * 1000)
            val output = process.inputStream.bufferedReader().readText()
            print(output)

            process.waitFor()
            timer.cancel()
        } catch (e: Exception) {
            process.destroyForcibly()
        }
    }
}

fun main(args: Array<String>) {
    CLI().main(args)
}