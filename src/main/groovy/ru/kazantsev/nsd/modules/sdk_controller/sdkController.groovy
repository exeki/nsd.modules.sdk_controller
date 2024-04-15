package ru.kazantsev.nsd.modules.sdk_controller

import ru.naumen.core.server.script.api.injection.InjectApi
import ru.naumen.metainfo.shared.IClassFqn

import static ru.kazantsev.nsd.sdk.global_variables.ApiPlaceholder.*

import ru.kazantsev.nsd.modules.web_api_components.RequestProcessor
import ru.kazantsev.nsd.modules.web_api_components.ProcessData
import ru.kazantsev.nsd.modules.web_api_components.ProcessUtilities
import ru.kazantsev.nsd.modules.web_api_components.ResponsePrototype
import ru.kazantsev.nsd.modules.web_api_components.WebApiException

import ru.naumen.core.server.script.api.metainfo.IMetaClassWrapper
import ru.naumen.core.shared.dto.ISDtObject

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class Constants {
    static final List<String> HAS_RELATED_METACLASS_CODES = ['catalogItem', 'catalogItemSet', 'backBOLinks', 'object', 'boLinks']
}

@InjectApi
class BranchCollector {
    @InjectApi
    static class UniquenessContainer {
        List<IMetaClassWrapper> collected = []
        List<String> detectedCodes = []

        Boolean add(IMetaClassWrapper metaClassWrapper) {
            if (metaClassWrapper.toString() in this.detectedCodes) return false
            this.detectedCodes.add(metaClassWrapper.toString())
            this.collected.add(metaClassWrapper)
            return true
        }

        Boolean add(IClassFqn fqn) {
            return this.add(api.metainfo.getMetaClass(fqn))
        }

        Boolean add(String metaClassCode) {
            return this.add(api.metainfo.getMetaClass(fqn))
        }
    }

    UniquenessContainer container = new UniquenessContainer()

    List<IMetaClassWrapper> process(String someMetaCode) {
        IMetaClassWrapper meta = api.metainfo.getMetaClass(someMetaCode)
        if (meta == null) return null
        return process(meta)
    }

    List<IMetaClassWrapper> process(IClassFqn someFqn) {
        IMetaClassWrapper meta = api.metainfo.getMetaClass(someFqn)
        if (meta == null) return null
        return process(meta)
    }

    List<IMetaClassWrapper> process(IMetaClassWrapper someMetaCodeWrapper) {
        if (container.add(someMetaCodeWrapper)) {
            List attrMetaClasses = someMetaCodeWrapper.getAttributes().findAll { it.getType().code in Constants.HAS_RELATED_METACLASS_CODES }.collect { it.getType().getRelatedMetaClass() }
            attrMetaClasses.each {
                if (it == null) return;
                process(it)
            }
            someMetaCodeWrapper.getChildren().each {
                if (it == null) return;
                process(it)
            }
        }
        return container.collected
    }
}

class Dto {
    static class MetaClassWrapperDto implements Serializable {
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

    static class AttributeDto implements Serializable {
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

    static class AttributeGroupDto implements Serializable {
        String title
        String code
        List<String> attributes
    }
}

private Dto.MetaClassWrapperDto getDtoFromMetaClassWrapper(IMetaClassWrapper metaClassWrapper) {
    return new Dto.MetaClassWrapperDto(
            fullCode: metaClassWrapper.getFqn().toString(),
            classCode: metaClassWrapper.getFqn().getId(),
            caseCode: metaClassWrapper.getFqn().getCase(),
            parent: metaClassWrapper.getParent().toString(),
            children: metaClassWrapper.getChildren().collect { it.toString() },
            title: metaClassWrapper.getTitle(),
            description: metaClassWrapper.getDescription(),
            hardcoded: metaClassWrapper.isHardcoded(),
            hasResponsible: metaClassWrapper.isHasResponsible(),
            hasWorkflow: metaClassWrapper.isHasResponsible(),
            attributes: metaClassWrapper.getAttributes().collect {
                return new Dto.AttributeDto(
                        title: it.getTitle(),
                        code: it.getCode(),
                        type: it.getType().code,
                        hardcoded: it.isHardcoded(),
                        required: it.isRequired(),
                        requiredInInterface: it.isRequiredInInterface(),
                        unique: it.isUnique(),
                        relatedMetaClass: (it.getType().code in Constants.HAS_RELATED_METACLASS_CODES) ? it.getType().getRelatedMetaClass().getId() : null,
                        description: it.getDescription()
                )
            },
            attributeGroups: metaClassWrapper.getAttributeGroups().collect {
                new Dto.AttributeGroupDto(
                        title: it.getTitle(),
                        code: it.getCode(),
                        attributes: it.getAttributeCodes()
                )
            }
    )
}

@SuppressWarnings("unused")
void getMetaClassBranchInfo(HttpServletRequest request, HttpServletResponse response, ISDtObject user) {
    RequestProcessor.create(new ProcessData.NoBody(), request, response, user, 'getMetaClassBranchInfo').assertHttpMethod('GET')
            .assertSuperuser()
            .process {
                ProcessData.NoBody processData ->
                    ProcessUtilities.Common utilities = new ProcessUtilities.Common(processData)
                    String meta = utilities.getStringParameterElseThrow("meta")
                    IMetaClassWrapper metaClassWrapper = api.metainfo.getMetaClass(meta)
                    if (metaClassWrapper == null) throw new WebApiException.BadRequest("Metaclass named $meta from url parameter not exists")
                    BranchCollector collector = new BranchCollector()
                    return new ResponsePrototype.Json(collector.process(metaClassWrapper).collect { getDtoFromMetaClassWrapper(it) })
            }
}

@SuppressWarnings("unused")
void getMetaClassBranchesInfo(HttpServletRequest request, HttpServletResponse response, ISDtObject user) {
    RequestProcessor.create(new ProcessData.NoBody(), request, response, user, 'getMetaClassBranchInfo').assertHttpMethod('GET')
            .assertSuperuser()
            .process {
                ProcessData.NoBody processData ->
                    ProcessUtilities.Common utilities = new ProcessUtilities.Common(processData)
                    String metasStr = utilities.getStringParameterElseThrow("metas")
                    List<String> metas = metasStr.split(',')
                    BranchCollector collector = new BranchCollector()
                    metas.each {meta ->
                        IMetaClassWrapper metaClassWrapper = api.metainfo.getMetaClass(meta)
                        if (metaClassWrapper == null) throw new WebApiException.BadRequest("Metaclass named $meta from url parameter not exists")
                        collector.process(metaClassWrapper)
                    }
                    return new ResponsePrototype.Json(collector.container.collected.collect { getDtoFromMetaClassWrapper(it) })
            }
}


@SuppressWarnings("unused")
void getMetaClassInfo(HttpServletRequest request, HttpServletResponse response, ISDtObject user) {
    RequestProcessor.create(new ProcessData.NoBody(), request, response, user, 'getMetaClassInfo')
            .assertHttpMethod('GET')
            .assertSuperuser()
            .process {
                ProcessData.NoBody processData ->
                    ProcessUtilities.Common utilities = new ProcessUtilities.Common(processData)
                    String meta = utilities.getStringParameterElseThrow("meta")
                    IMetaClassWrapper metaClassWrapper = api.metainfo.getMetaClass(meta)
                    if (metaClassWrapper == null) throw new WebApiException.NotFound("Cant find metaclass named $meta")
                    return new ResponsePrototype.Json(getDtoFromMetaClassWrapper(metaClassWrapper))
            }
}