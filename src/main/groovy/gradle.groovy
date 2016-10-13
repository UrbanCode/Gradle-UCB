#!/usr/bin/env groovy

import com.urbancode.air.*

final def apTool = new AirPluginTool(this.args[0], this.args[1])
final def props = apTool.getStepProperties()
final def workDir = new File('.').canonicalFile

final def directoryOffset = props['directoryOffset']
final def gradleFileName = props['gradleFileName']
final def taskNames = props['taskNames']
final def gradleOptions = props['gradleOptions']
final def jvmProperties = props['jvmProperties']
final def sysProperties = props['sysProperties']
final def projProperties = props['projProperties']
final def scriptContent = props['scriptContent']
final def GRADLE_HOME = props['gradleHome']
final def JAVA_HOME = props['javaHome']

//
// Validation
//

if (directoryOffset) {
    workDir = new File(workDir, directoryOffset).canonicalFile
}

if (workDir.isFile()) {
    throw new IllegalArgumentException("Working directory ${workDir} is a file!")
}

if (gradleFileName == null) {
    throw new IllegalStateException("Gradle Script File not specified.");
}

//
// Create workDir and gradleFile
//

// ensure work-dir exists
workDir.mkdirs()

final def gradleFile = new File(workDir, gradleFileName)

// check if script content was specified, if so, we need to write out the file
boolean deleteOnExit = false
if (scriptContent) {
    gradleFile.text = scriptContent
    deleteOnExit = true
}

try {
    CommandHelper cmdHelper = new CommandHelper(workDir)

    //
    // Build Command Line
    //
    def isWindows = apTool.isWindows
    def gradle = 'gradle'
    def gradlew = new File(workDir, gradle + 'w' + (isWindows ? '.bat' : ''))
    if (gradlew.exists()) {
        if (!isWindows) {
            cmdHelper.runCommand("Setting execute permission for ${gradlew.name}", ["chmod", "+x", gradlew.name])
        }
        gradle = (isWindows ? "" : "./") + gradlew.name
    }
    else if (GRADLE_HOME) {
        gradle = new File(GRADLE_HOME, "bin/gradle" + (isWindows ? ".bat" : "")).absolutePath
    }

    def commandLine = [gradle]

    if (gradleOptions) {
        gradleOptions.readLines().each() { gradleProperty ->
            if (gradleProperty) {
                commandLine.add(gradleProperty)
            }
        }
    }

    commandLine.add("-b")
    commandLine.add(gradleFile.absolutePath)

    if (sysProperties) {
        sysProperties.eachLine { property ->
            if (property) {
                if (!property.startsWith("-D")) {
                    property = "-D" + property
                }
                commandLine.add(property)
            }
        }
    }

    if (projProperties) {
        projProperties.eachLine { property ->
            if (property) {
                if (!property.startsWith("-P")) {
                    property = "-P" + property
                }
                commandLine.add(property)
            }
        }
    }

    if (taskNames) {
        taskNames.split("\\s+").each() { taskName ->
            if (taskName) {
                commandLine.add(taskName)
            }
        }
    }

    //
    // Launch Process
    //
    if (GRADLE_HOME) {
        cmdHelper.addEnvironmentVariable("GRADLE_HOME", GRADLE_HOME)
    }
    if (JAVA_HOME) {
        cmdHelper.addEnvironmentVariable("JAVA_HOME", JAVA_HOME)
    }
    if (jvmProperties) {
        def javaOpts = jvmProperties.readLines().join(' ')
        cmdHelper.addEnvironmentVariable("JAVA_OPTS", javaOpts)
    }
    cmdHelper.runCommand("Building project", commandLine)
}
catch (FileNotFoundException ex){
    throw new FileNotFoundException("[Error] Could not find files or folders needed for the gradle command. " +
        "Confirm directory paths specified for the project, Gradle home, Java home, and script file. $ex", ex)
}
catch (RuntimeException ex){
    throw new RuntimeException("[Error] Exception found during runtime. $ex", ex)
}
catch (Exception ex){
    throw new Exception("[Error] Unknown error interrupted the Gradle command. $ex", ex)
}
finally {
    if (deleteOnExit) {
        gradleFile.delete()
    }
}
