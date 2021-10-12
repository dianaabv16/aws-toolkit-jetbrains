// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.dynamic

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import software.amazon.awssdk.arns.Arn
import software.amazon.awssdk.services.cloudcontrol.CloudControlClient
import software.amazon.awssdk.services.cloudformation.CloudFormationClient
import software.amazon.awssdk.services.cloudformation.model.RegistryType
import software.amazon.awssdk.services.cloudformation.model.Visibility
import software.aws.toolkits.jetbrains.core.ClientBackedCachedResource
import software.aws.toolkits.jetbrains.core.Resource

object CloudControlApiResources {

    fun listResources(typeName: String): Resource.Cached<List<DynamicResource>> =
        ClientBackedCachedResource(CloudControlClient::class, "cloudcontrolapi.dynamic.resources.$typeName") {
            this.listResourcesPaginator {
                it.typeName(typeName)
            }.flatMap {
                it.resourceDescriptions().map { resource ->
                    DynamicResource(resourceTypeFromResourceTypeName(it.typeName()), resource.identifier())
                }
            }
        }

    fun resourceTypeFromResourceTypeName(typeName: String): ResourceType {
        val (_, svc, type) = typeName.split("::")
        return ResourceType(typeName, svc, type)
    }

    fun listResources(resourceType: ResourceType): Resource.Cached<List<DynamicResource>> = listResources(resourceType.fullName)

    fun getResourceDisplayName(identifier: String): String =
        if (identifier.startsWith("arn:")) {
            Arn.fromString(identifier).resourceAsString()
        } else {
            identifier
        }

    fun getResourceSchema(resourceType: String): Resource.Cached<VirtualFile> =
        ClientBackedCachedResource(CloudFormationClient::class, "cloudformation.dynamic.resources.schema.$resourceType") {
            val schema = this.describeType {
                it.type(RegistryType.RESOURCE)
                it.typeName(resourceType)
            }.schema()
            LightVirtualFile("${resourceType}Schema.json", schema)
        }

    fun listTypes(): Resource.Cached<List<String>> = ClientBackedCachedResource(
        CloudFormationClient::class, "cloudformation.listTypes"
    ) {
        this.listTypesPaginator {
            it.visibility(Visibility.PUBLIC)
            it.type(RegistryType.RESOURCE)
        }.flatMap { it.typeSummaries().map { it.typeName() } }
    }
}

data class ResourceDetails(val operations: List<PermittedOperation>, val arnRegex: String?, val documentation: String?)

enum class PermittedOperation {
    CREATE, READ, UPDATE, DELETE, LIST;
}
