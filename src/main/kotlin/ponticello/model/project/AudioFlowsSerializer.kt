package ponticello.model.project

import hextant.serial.readJson
import hextant.serial.writeJson
import javafx.scene.paint.Color
import kotlinx.serialization.Serializable
import ponticello.impl.ColorSerializer
import ponticello.impl.Decimal
import ponticello.impl.Logger
import ponticello.impl.json
import ponticello.model.flow.AudioFlow
import ponticello.model.flow.AudioFlowGroup
import ponticello.model.flow.AudioFlows
import ponticello.model.obj.withName
import ponticello.model.registry.ObjectListSerializer
import reaktive.value.now
import reaktive.value.reactiveVariable
import java.io.File

object AudioFlowsSerializer : SingleFileComponentSerializer<AudioFlows>(
    ObjectListSerializer(AudioFlowGroup.serializer(), ::AudioFlows)
) {
    override fun serializeComponent(value: AudioFlows, dataDirectory: File) {
        val groupsDir = dataDirectory.resolve("flows")
        groupsDir.mkdir()
        var everythingOk = true
        for (group in value) {
            val flowOrder = group.flows.map { flow -> flow.name.now }
            val info = AudioFlowGroupInfo(group.isActive.now, group.yPosition.now, group.associatedColor.now, flowOrder)
            val groupName = group.name.now
            val infoFile = groupsDir.resolve("$groupName.json")
            try {
                infoFile.writeJson(info, json)
            } catch (e: Exception) {
                Logger.error("Error while writing info file for group $groupName to $infoFile!", e)
                everythingOk = false
            }
            val flowsDir = groupsDir.resolve(groupName)
            flowsDir.mkdir()
            for (flow in group.flows) {
                val flowName = flow.name.now
                val file = flowsDir.resolve("$flowName.json")
                try {
                    file.writeJson(flow, json)
                } catch (e: Exception) {
                    Logger.error("Error while writing flow $flowName to $file!", e)
                    everythingOk = false
                }
            }
            // Delete files that don't belong to any flow
            try {
                val validFiles = group.flows.map { flow -> "${flow.name.now}.json" }.toSet()
                flowsDir.listFiles()?.forEach { file ->
                    if (file.isFile && file.extension == "json" && !validFiles.contains(file.name)) {
                        file.delete()
                    }
                }
            } catch (e: Exception) {
                Logger.error("Error while deleting orphaned files in group $groupName!", e)
                everythingOk = false
            }
        }
        // Delete orphaned group directories and info files
        val validGroupDirs = value.map { grp -> grp.name.now }.toSet()
        val validGroupFiles = value.map { grp -> "${grp.name.now}.json" }.toSet()
        try {
            groupsDir.listFiles()?.forEach { file ->
                if (file.isDirectory && !validGroupDirs.contains(file.name)) {
                    file.deleteRecursively()
                }
                if (file.isFile && file.extension == "json" && !validGroupFiles.contains(file.name)) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            Logger.error("Error while deleting orphaned directories and info files in directory $groupsDir!", e)
            everythingOk = false
        }
        if (everythingOk) {
            dataDirectory.resolve("${component.name}.json").delete()
        }
    }

    override fun deserializeComponent(dataDirectory: File): AudioFlows {
        val groupsDir = dataDirectory.resolve("flows")
        if (!groupsDir.exists()) return super.deserializeComponent(dataDirectory)

        val infoFiles = groupsDir.listFiles { f -> f.extension == "json" } ?: return AudioFlows.createDefault()
        val groups = mutableListOf<AudioFlowGroup>()
        for (infoFile in infoFiles) {
            val groupName = infoFile.nameWithoutExtension
            val info = infoFile.readJson<AudioFlowGroupInfo>(json)

            val flows = mutableListOf<AudioFlow>()
            val flowsDir = groupsDir.resolve(groupName)
            val flowOrder = info.flowOrder
                ?: flowsDir.listFiles { f -> f.extension == "json" }?.map(File::nameWithoutExtension) ?: emptyList()
            for (flowName in flowOrder) {
                val flowFile = flowsDir.resolve("$flowName.json")
                if (!flowFile.isFile) {
                    Logger.error("File $flowFile is missing!")
                    continue
                }
                try {
                    val flow = flowFile.readJson<AudioFlow>(json)
                    flows.add(flow)
                } catch (e: Exception) {
                    Logger.error("Error while reading flow ${flowFile.nameWithoutExtension} from $flowFile!", e)
                }
            }

            val group = info.createGroup(flows).withName(groupName)
            groups.add(group)
        }
        groups.sortBy { grp -> grp.yPosition.now }
        return AudioFlows(groups)
    }

    @Serializable
    private data class AudioFlowGroupInfo(
        val isActive: Boolean,
        val yPosition: Decimal,
        @Serializable(ColorSerializer::class) val color: Color,
        val flowOrder: List<String>? = null,
    ) {
        fun createGroup(flows: MutableList<AudioFlow>) = AudioFlowGroup(
            reactiveVariable(isActive), reactiveVariable(yPosition), reactiveVariable(color),
            AudioFlowGroup.AudioFlowList(flows)
        )
    }
}