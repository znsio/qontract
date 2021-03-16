package run.qontract.core.wsdl.parser

import run.qontract.core.pattern.XMLPattern
import run.qontract.core.value.XMLNode
import run.qontract.core.wsdl.parser.message.MessageTypeInfoParser
import run.qontract.core.wsdl.parser.message.MessageTypeInfoParserStart
import run.qontract.core.wsdl.parser.message.isPrimitiveType
import run.qontract.core.wsdl.parser.operation.SOAPOperationTypeInfo
import run.qontract.core.wsdl.payload.SoapPayloadType
import java.net.URI

const val TYPE_NODE_NAME = "QONTRACT_TYPE"

class SOAP11Parser(private val wsdl: WSDL): SOAPParser {
    override fun convertToGherkin(url: String): String {
        val portType = wsdl.getPortType()

        val operationsTypeInfo = wsdl.operations.map {
            parseOperation(it, url, wsdl, portType)
        }

        val featureName = wsdl.getServiceName()

        val featureHeading = "Feature: $featureName"

        val indent = "    "
        val gherkinScenarios = operationsTypeInfo.map { it.toGherkinScenario(indent, indent) }

        return listOf(featureHeading).plus(gherkinScenarios).joinToString("\n\n")
    }

    private fun parseOperation(bindingOperationNode: XMLNode, url: String, wsdl: WSDL, portType: XMLNode): SOAPOperationTypeInfo {
        val operationName = bindingOperationNode.getAttributeValue("name")

        val soapAction = bindingOperationNode.getAttributeValueAtPath("operation", "soapAction")

        val portOperationNode = portType.findNodeByNameAttribute(operationName)

        val requestTypeInfo = parsePayloadTypes(
            portOperationNode,
            operationName,
            SOAPMessageType.Input,
            wsdl,
            emptyMap()
        )

        val responseTypeInfo = parsePayloadTypes(
            portOperationNode,
            operationName,
            SOAPMessageType.Output,
            wsdl,
            requestTypeInfo.types
        )

        val path = URI(url).path

        return SOAPOperationTypeInfo(
            path,
            operationName,
            soapAction,
            responseTypeInfo.types,
            requestTypeInfo.soapPayload,
            responseTypeInfo.soapPayload
        )
    }

    private fun parsePayloadTypes(
        portOperationNode: XMLNode,
        operationName: String,
        soapMessageType: SOAPMessageType,
        wsdl: WSDL,
        existingTypes: Map<String, XMLPattern>
    ): SoapPayloadType {
        var messageTypeInfoParser: MessageTypeInfoParser = MessageTypeInfoParserStart(wsdl, portOperationNode, soapMessageType, existingTypes, operationName)

        while(messageTypeInfoParser.soapPayloadType == null) {
            messageTypeInfoParser = messageTypeInfoParser.execute()
        }

        return messageTypeInfoParser.soapPayloadType!!
    }
}

fun hasSimpleTypeAttribute(element: XMLNode): Boolean = element.attributes.containsKey("type") && isPrimitiveType(element)