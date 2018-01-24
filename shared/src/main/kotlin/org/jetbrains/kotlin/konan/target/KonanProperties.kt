/*
 * Copyright 2010-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed -> in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.konan.properties

import org.jetbrains.kotlin.konan.target.KonanTarget
import org.jetbrains.kotlin.konan.util.DependencyProcessor

interface TargetableExternalStorage {
    fun targetString(key: String): String? 
    fun targetList(key: String): List<String> 
    fun hostString(key: String): String? 
    fun hostList(key: String): List<String> 
    fun hostTargetString(key: String): String? 
    fun hostTargetList(key: String): List<String> 
    fun absolute(value: String?): String
    fun downloadDependencies()
}

interface KonanPropertyValues: TargetableExternalStorage {

    val target: KonanTarget

    val llvmHome get() = hostString("llvmHome")
    val llvmVersion get() = hostString("llvmVersion")

    // TODO: Delegate to a map?
    val llvmLtoNooptFlags get() = targetList("llvmLtoNooptFlags")
    val llvmLtoOptFlags get() = targetList("llvmLtoOptFlags")
    val llvmLtoFlags get() = targetList("llvmLtoFlags")
    val llvmLtoDynamicFlags get() = targetList("llvmLtoDynamicFlags")
    val entrySelector get() = targetList("entrySelector")
    val linkerOptimizationFlags get() = targetList("linkerOptimizationFlags")
    val linkerKonanFlags get() = targetList("linkerKonanFlags")
    val linkerNoDebugFlags get() = targetList("linkerNoDebugFlags")
    val linkerDynamicFlags get() = targetList("linkerDynamicFlags")
    val llvmDebugOptFlags get() = targetList("llvmDebugOptFlags")
    val targetSysRoot get() = targetString("targetSysRoot")
    val libffiDir get() = targetString("libffiDir")
    // TODO: This one should belong to LinuxPropertyValues,
    // but as of now that would break the dependency downloader.
    val gccToolchain get() = hostString("gccToolchain")

    // Notice: these ones are host-target.
    val targetToolchain get() = hostTargetString("targetToolchain")

    val absoluteTargetSysRoot get() = absolute(targetSysRoot)
    val absoluteTargetToolchain get() = absolute(targetToolchain)
    val absoluteGccToolchain get() = absolute(gccToolchain)
    val absoluteLlvmHome get() = absolute(llvmHome)
    val absoluteLibffiDir get() = absolute(libffiDir)

}

interface NonApplePropertyValues: KonanPropertyValues {
    val targetArg get() = targetString("quadruple")
}

interface ApplePropertyValues: KonanPropertyValues {
    val arch get() = targetString("arch")!!
    val osVersionMin get() = targetString("osVersionMin")!!
    val osVersionMinFlagLd get() = targetString("osVersionMinFlagLd")!!
}

interface MingwPropertyValues: NonApplePropertyValues {
    val mingwWithLlvm: String?
        get() { 
            // TODO: make it a property in the konan.properties.
            // Use (equal) llvmHome fow now.
            return targetString("llvmHome")
        }
}

interface LinuxPropertyValues: NonApplePropertyValues {
    val libGcc get() = targetString("libGcc")!!
    val dynamicLinker get() = targetString("dynamicLinker")!!
    val pluginOptimizationFlags get() = targetList("pluginOptimizationFlags")
    val abiSpecificLibraries get() = targetList("abiSpecificLibraries")
}

interface LinuxMIPSPropertyValues: LinuxPropertyValues
interface RaspberryPiPropertyValues: LinuxPropertyValues
interface AndroidPropertyValues: LinuxPropertyValues

interface WasmPropertyValues: NonApplePropertyValues {
    val s2wasmFlags get() = targetList("s2wasmFlags")
}

open class KonanPropertiesLoader(override val target: KonanTarget, val properties: Properties, val baseDir: String? = null) : KonanPropertyValues {
    val dependencies get() = hostTargetList("dependencies")

    override fun downloadDependencies() {
        dependencyProcessor!!.run()
    }

    override fun targetString(key: String): String? 
        = properties.targetString(key, target)
    override fun targetList(key: String): List<String> 
        = properties.targetList(key, target)
    override fun hostString(key: String): String? 
        = properties.hostString(key)
    override fun hostList(key: String): List<String> 
        = properties.hostList(key)
    override fun hostTargetString(key: String): String? 
        = properties.hostTargetString(key, target)
    override fun hostTargetList(key: String): List<String> 
        = properties.hostTargetList(key, target)
    override fun absolute(value: String?): String =
            dependencyProcessor!!.resolveRelative(value!!).absolutePath
    private val dependencyProcessor  by lazy {
        baseDir?.let { DependencyProcessor(java.io.File(it), this) }
    }
}

class LinuxProperties(target: KonanTarget, properties: Properties, baseDir: String?)
    : LinuxPropertyValues, KonanPropertiesLoader(target, properties, baseDir)

class LinuxMIPSProperties(target: KonanTarget, properties: Properties, baseDir: String?)
    : LinuxMIPSPropertyValues , KonanPropertiesLoader(target, properties, baseDir)

class AndroidProperties(target: KonanTarget, properties: Properties, baseDir: String?)
    : AndroidPropertyValues , KonanPropertiesLoader(target, properties, baseDir)

class AppleProperties(target: KonanTarget, properties: Properties, baseDir: String?)
    : ApplePropertyValues,  KonanPropertiesLoader(target, properties, baseDir)

class MingwProperties(target: KonanTarget, properties: Properties, baseDir: String?)
    : MingwPropertyValues, KonanPropertiesLoader(target, properties, baseDir)

class WasmProperties(target: KonanTarget, properties: Properties, baseDir: String?)
    : WasmPropertyValues, KonanPropertiesLoader(target, properties, baseDir)


internal fun konanProperties(target: KonanTarget, properties: Properties, baseDir: String?) = when (target)  {
        KonanTarget.LINUX, KonanTarget.RASPBERRYPI ->
            LinuxProperties(target, properties, baseDir)
        KonanTarget.LINUX_MIPS32, KonanTarget.LINUX_MIPSEL32 ->
            LinuxMIPSProperties(target, properties, baseDir)
        KonanTarget.MACBOOK, KonanTarget.IPHONE, KonanTarget.IPHONE_SIM ->
            AppleProperties(target, properties, baseDir)
        KonanTarget.ANDROID_ARM32, KonanTarget.ANDROID_ARM64 ->
            AndroidProperties(target, properties, baseDir)
        KonanTarget.MINGW ->
            MingwProperties(target, properties, baseDir)
        KonanTarget.WASM32 ->
            WasmProperties(target, properties, baseDir)
    }

