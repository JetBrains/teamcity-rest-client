package org.jetbrains.teamcity.rest.framework

import java.io.StringWriter
import javax.xml.stream.XMLOutputFactory
import javax.xml.stream.XMLStreamWriter

fun xml(init: XMLStreamWriter.() -> Unit): String {
    val stringWriter = StringWriter()
    val xmlStreamWriter = XMLOutputFactory.newFactory().createXMLStreamWriter(stringWriter)
    init(xmlStreamWriter)
    xmlStreamWriter.flush()
    return stringWriter.toString()
}

fun XMLStreamWriter.element(name: String, init: XMLStreamWriter.() -> Unit): XMLStreamWriter {
    writeStartElement(name)
    init()
    writeEndElement()
    return this
}

fun XMLStreamWriter.element(name: String, value: String): XMLStreamWriter {
    writeStartElement(name)
    writeCharacters(value)
    writeEndElement()
    return this
}

fun XMLStreamWriter.attribute(name: String, value: String) = writeAttribute(name, value)

fun XMLStreamWriter.property(name: String, value: String) {
    element("property") {
        attribute("name", name)
        attribute("value", value)
    }
}
