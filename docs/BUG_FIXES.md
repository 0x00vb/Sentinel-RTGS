# Bug Fixes Report - 2025-11-27

## 1. XML Validation Failure for pacs.008 Messages

**Issue:**
The application logs showed frequent XML validation errors for incoming `pacs.008` messages:
`XML validation failed: cvc-complex-type.2.4.a: Invalid content was found starting with element '{"urn:iso:std:iso:20022:tech:xsd:pacs.008.001.10":Cdtr}'. One of '{"urn:iso:std:iso:20022:tech:xsd:pacs.008.001.10":CdtrAgt}' is expected.`

**Root Cause:**
The `TrafficSimulatorService.java` was generating `pacs.008` XML messages with the `Cdtr` (Creditor) element appearing before the `CdtrAgt` (Creditor Agent) element. The `pacs.008.001.10.xsd` schema strictly requires `CdtrAgt` to precede `Cdtr` within the `CdtTrfTxInf` complex type.

**Fix:**
Modified `backend/src/main/java/com/example/backend/service/TrafficSimulatorService.java` to swap the order of `Cdtr` and `CdtrAgt` elements in the `generatePacs008Xml` method, ensuring compliance with the XSD schema.

## 2. Marshalling Exception for pacs.002 Status Reports

**Issue:**
The application failed to send status reports (pacs.002) with the following error:
`javax.xml.bind.MarshalException: null ... Caused by: com.sun.istack.SAXException2: unable to marshal type "com.example.backend.iso20022.pacs002.Document" as an element because it is missing an @XmlRootElement annotation`

**Root Cause:**
The JAXB-generated `Document` class for `pacs.002` lacks the `@XmlRootElement` annotation. This is common with XJC generation for complex types. The `MessageOutboundService` was attempting to marshal the `Document` object directly, but the marshaller requires a root element definition (typically provided by a `JAXBElement` wrapper or the annotation).

**Fix:**
Modified `backend/src/main/java/com/example/backend/service/MessageOutboundService.java` in the `generateStatusReportXml` method. Instead of marshalling the `Document` object directly, the code now uses `ObjectFactory` to wrap the `Document` in a `JAXBElement` using `objectFactory.createDocument(document)`. This provides the necessary root element information (name and namespace) to the marshaller.

## 3. Unmarshalling Exception for pacs.008 Messages

**Issue:**
After fixing the XML structure, `pacs.008` processing failed with:
`XML processing failed: unexpected element (uri:"urn:iso:std:iso:20022:tech:xsd:pacs.008.001.10", local:"Document"). Expected elements are (none)`

**Root Cause:**
Similar to the `pacs.002` issue, the `JAXBContext` for `pacs.008` was initialized using `Document.class` which lacks `@XmlRootElement`. The unmarshaller could not map the incoming root element `Document` to the JAXB class.

**Fix:**
Modified `backend/src/main/java/com/example/backend/service/XmlProcessingService.java`:
1. Updated `JAXBContext.newInstance(Document.class)` to `JAXBContext.newInstance(ObjectFactory.class)`.
2. Updated the `unmarshalXml` method to handle the `JAXBElement` wrapper returned by the unmarshaller when using `ObjectFactory`.