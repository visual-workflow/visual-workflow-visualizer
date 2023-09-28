package com.kgignatyev.temporal.visualwf.renderer

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.kgignatyev.temporal.visualwf.api.WFInfo
import com.kgignatyev.temporal.visualwf.api.WFStateInfo
import java.io.BufferedReader
import java.io.InputStreamReader


val testUML = """   
    @startuml
    scale 600 width



    [*] -> State1
    State1 --> State2 : Succeeded
    State1 --> [*] : Aborted
    State2 --> State3 : Succeeded
    State2 --> [*] : Aborted
    state State3 {
      state "Accumulate Enough Data\nLong State Name" as long1
      long1 : Just a test
      [*] --> long1
      long1 --> long1 : New Data
      long1 --> ProcessData : Enough Data
    }
    State3 --> State3 : Failed
    State3 --> [*] : Succeeded / Save Result
    State3 --> [*] : Aborted

    @enduml
    
""".trimIndent()


class PlantUMLHelper(
    val plantumlBaseUrl:String = "http://www.plantuml.com/plantuml/png/",
    val successColor: String = "#00FF00", val failureColor: String = "#FF0000") {

    fun toPlantUmlPngURL( uml: String, wfInfo: WFInfo): String {
        val  enhancedUML = enhanceUML(uml, wfInfo)
        val encoded = textToHex(enhancedUML)
        return "https://www.plantuml.com/plantuml/png/~h$encoded"
    }

    fun textToHex(text: String): String {
        val sb = StringBuilder()
        for (b in text.toByteArray()) {
            sb.append(String.format("%02X", b))
        }
        return sb.toString()
    }

    fun enhanceUML(uml: String, wfInfo: WFInfo): String {

        val umlAdditions = StringBuilder()
        wfInfo.activeStates.forEach {
            val color = if (it.isError != null && it.isError) failureColor else successColor
            umlAdditions.append("state ${it.stateName} ${color} \n")
        }
        umlAdditions.append("\n")
        if (wfInfo.legend != null) {
            umlAdditions.append(
                """
                   legend top left
                   ${wfInfo.legend}
                   endlegend
                   
                   @enduml 
                """.trimIndent()
            )
        }
        return uml.replace("@enduml", umlAdditions.toString())
    }

}


object VisualizeWF {

    @JvmStatic
    fun main(args: Array<String>) {
        val wfId = System.getenv()["WFID"] ?: throw Exception("WFID env variable is not set")
        println("WorkflowID = $wfId")
        val om = ObjectMapper()
        val plantumlQueryResult = runCommand(listOf("temporal", "workflow", "query",
            "--type","getPlantUMLWorkflowDefinition",
             "--workflow-id", wfId,
            ))
        println(plantumlQueryResult)
        //NOTE: this is a hack because 'temporal' not yet supports --raw=true for query command
        val components = plantumlQueryResult.split("\n").toList()
        val r = om.readTree(components[1]) as ArrayNode
        val plantUML_wf_definition = r.get(0).asText()
        println(plantUML_wf_definition)

        val wfStateQueryResult = runCommand(listOf("temporal", "workflow", "query",
            "--type","getWorkflowInfo",
            "--workflow-id", wfId,
        ))
        println(wfStateQueryResult)
        val components2 = wfStateQueryResult.split("\n").toList()
        val r2 = om.readTree(components2[1]) as ArrayNode
        println("$r2")
        val wfStateInfo  = r2.get(0) as ObjectNode
        val helper = PlantUMLHelper()
        val legend = wfStateInfo.get("legend")?.asText()?: ""
        val activeStates = wfStateInfo.get("activeStates") as ArrayNode
        val activeState = activeStates.get(0) as ObjectNode
        val activeStateName = activeState.get("stateName").asText()
        val isError = activeState.get("isError")?.asBoolean()?: false
        val wfInfo = WFInfo().setLegend(legend)
            .setActiveStates( listOf( WFStateInfo().setStateName(activeStateName).setError(isError) ) )
        val plantUmlPngURL = helper.toPlantUmlPngURL(plantUML_wf_definition, wfInfo)
        println(plantUmlPngURL)
        println( runCommand(listOf("python3", "-m", "webbrowser", plantUmlPngURL)))

//        val testWfInfo = WFInfo().setLegend("a legend")
//            .setActiveStates( listOf( WFStateInfo().setStateName("State1"), WFStateInfo().setStateName("State2").setError(true) ) )
//        println(helper.toPlantUmlPngURL(testUML, testWfInfo))
    }


    fun runCommand(commandComponents:List<String>):String {
        val processBuilder = ProcessBuilder()
        processBuilder.command(commandComponents)
        val process: Process = processBuilder.start()
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        process.waitFor()
        return reader.readText()
    }
}


