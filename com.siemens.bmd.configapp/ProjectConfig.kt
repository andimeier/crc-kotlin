package com.siemens.bmd.configapp

data class Position(
    val label: String, // the textual representation used in the user interface user
    val value: Int, // the numeric representation of the position stored in the config
    val radioChannel1: Int,
    val slot: Int,
    val radioChannel2: Int,
    val linkDirection2: Int,
    val radioChannel3: Int,
    val linkDirection2: Int,
    val orientation: Int,
    val networkId: Int,
)

data class Car(
    val label: String,
    val value: Int,
    val positions: Position[],
)

data class Project(
    val label: String, 
    val value: Int, 
    val description: String,
)

data class ProjectConfig(
    val project: Project, 
    val trains: Int[], 
    val cars: Car[],
)


/**
 * Read project config from a YAML config file.
 * 
 * @param path the path to the YAML config file
 * @return the parsed project config object
 */
fun loadFromFile(path: Path): ProjectConfig {
    val mapper = ObjectMapper(YAMLFactory()) // enable YAML parsing
    mapper.registerModule(KotlinModule()) // enable Kotlin support

    return Files.newBufferedReader(path).use {
        mapper.readValue(it, ProjectConfig::class.java)
    }
}