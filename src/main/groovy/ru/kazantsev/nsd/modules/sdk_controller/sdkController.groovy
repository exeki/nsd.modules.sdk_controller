package ru.kazantsev.nsd.modules.sdk_controller
import static ru.kazantsev.nsd.sdk.global_variables.ApiPlaceholder.*

import groovy.transform.Field

import ru.kazantsev.nsd.modules.web_api_components.RequestProcessor
import ru.kazantsev.nsd.modules.web_api_components.ProcessData
import ru.kazantsev.nsd.modules.web_api_components.ProcessUtilities
import ru.kazantsev.nsd.modules.web_api_components.ResponsePrototype
import ru.kazantsev.nsd.modules.web_api_components.WebApiException

import ru.naumen.core.server.script.api.metainfo.IMetaClassWrapper
import ru.naumen.core.shared.dto.ISDtObject

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@Field List<String> HAS_RELATED_METACLASS_CODES = ['catalogItem', 'catalogItemSet', 'backBOLinks', 'object', 'boLinks']

class MetaClassWrapperDto implements Serializable {
    String title
    String fullCode
    String caseCode
    String classCode
    String parent
    String description
    Boolean hasResponsible
    Boolean hasWorkflow
    Boolean hardcoded
    List<String> children
    List<AttributeDto> attributes
    List<AttributeGroupDto> attributeGroups
}

class AttributeDto implements Serializable {
    String title
    String code
    String type
    Boolean hardcoded
    Boolean required
    Boolean requiredInInterface
    Boolean unique
    String relatedMetaClass
    String description
}

class AttributeGroupDto implements Serializable{
    String title
    String code
    List<String> attributes
}

void getMetaClassInfo(HttpServletRequest request, HttpServletResponse response, ISDtObject user) {
    RequestProcessor.create(new ProcessData.NoBody(), request, response, user, 'getMetaClassInfo')
            .assertHttpMethod('GET')
            .assertSuperuser()
            .process {
                ProcessData.NoBody processData ->
                    ProcessUtilities.Common utilities = new ProcessUtilities.Common(processData)
                    String meta = utilities.getStringParameterElseThrow("meta")
                    IMetaClassWrapper metaClassWrapper = api.metainfo.getMetaClass(meta)
                    if(metaClassWrapper == null) {
                        throw new WebApiException.NotFound("Cant find metaclass named $meta")
                    }
                    MetaClassWrapperDto result = new MetaClassWrapperDto(
                            fullCode: metaClassWrapper.getFqn().toString(),
                            classCode: metaClassWrapper.getFqn().getId(),
                            caseCode: metaClassWrapper.getFqn().getCase(),
                            parent: metaClassWrapper.getParent().toString(),
                            children: metaClassWrapper.getChildren().collect{it.toString()},
                            title: metaClassWrapper.getTitle(),
                            description: metaClassWrapper.getDescription(),
                            hardcoded: metaClassWrapper.isHardcoded(),
                            hasResponsible: metaClassWrapper.isHasResponsible(),
                            hasWorkflow: metaClassWrapper.isHasResponsible(),
                            attributes : metaClassWrapper.getAttributes().collect{
                                return new AttributeDto(
                                        title: it.getTitle(),
                                        code:it.getCode(),
                                        type:it.getType().code,
                                        hardcoded: it.isHardcoded(),
                                        required: it.isRequired(),
                                        requiredInInterface: it.isRequiredInInterface(),
                                        unique: it.isUnique(),
                                        relatedMetaClass: (it.getType().code in HAS_RELATED_METACLASS_CODES) ? it.getType().getRelatedMetaClass().getId() : null,
                                        description: it.getDescription()
                                )
                            },
                            attributeGroups: metaClassWrapper.getAttributeGroups().collect{
                                new AttributeGroupDto(
                                        title: it.getTitle(),
                                        code:it.getCode(),
                                        attributes: it.getAttributeCodes()
                                )
                            }

                    )
                    return new ResponsePrototype.Json(result)
            }
}