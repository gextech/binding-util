package gex.serling.binding

import org.apache.commons.lang.StringUtils
import org.codehaus.groovy.grails.commons.DomainClassArtefactHandler
import org.codehaus.groovy.runtime.InvokerHelper
import org.springframework.stereotype.Component
import org.springframework.util.ReflectionUtils

import java.lang.reflect.Field
import org.codehaus.groovy.grails.commons.DefaultGrailsDomainClass

@Component
class Util {

  static List avoidList = ['metaClass', 'class']
  
  List<DynamicBinding> dynamicBindings
  Set<String> exclusions
  
  
  Util(){
    dynamicBindings = []
    exclusions = []
  }

  void registerBinding(DynamicBinding bindingEntry){
    dynamicBindings = dynamicBindings ?: []
    dynamicBindings.add(bindingEntry)
  }

  void registerExclusions(List<String> exclusions){
    this.exclusions = exclusions ?: []
    this.exclusions.addAll(exclusions)
  }


  def static bind(Object source, Class target, List<String> avoid = []) {
    def invalidFields = avoidList + avoid
    def dto = target.newInstance()
    bind(source, dto, invalidFields)
  }

  def static bind(Object source, Object destination, List<String> avoid = []) {
    new Util(exclusions: avoid, dynamicBindings: []).dynamicBind(source, destination)
  }

  def dynamicBind(Object source, Class target) {
    def dto = target.newInstance()
    dynamicBind(source, dto)
  }

  def dynamicBind(Object source, Object destination){
    if(source == null){
      return null
    }

    def invalidFields = avoidList + exclusions
    println "Invalid Fields: ${invalidFields}"
    
    def destinationProperties = getValidDestinationProperties(destination, invalidFields, source)
    
    Set<String> destinationsProps = destinationProperties.destinationsProps
    Map entities = destinationProperties.entities

    def props = getSourceProperties(source, destinationsProps)
    props += entities

    use(InvokerHelper) {
      props.each { attribute ->
        def prop = source.getProperty(attribute.key)

        def dynamicBinding = getDynamicBindingValue(source, destination, attribute)
        if(dynamicBinding){
          destination.setProperty(attribute.key, dynamicBinding)
        }else {
          if (DomainClassArtefactHandler.isDomainClass(prop.getClass())) {
            processDomainClass(source, destination, attribute, entities)
          } else if (prop.getClass().isEnum()) {
            processEnum(source, destination, attribute, entities)
          } else if (prop instanceof Collection) {
            processCollection(source, destination, attribute, entities)
          } else {
            processSimpleProperty(source, destination, attribute, entities)
          }
        }
      }
    }
    
    destination
  }
  
  def processDomainClass(Object source,  Object destination,  def attribute, Map entities){
    def prop = source.getProperty(attribute.key)
    def propName = attribute.key
    
    if (entities[attribute.key]) {
      entities[attribute.key].each { attr ->
        destination.setProperty(attribute.key + attr.toString().capitalize(), prop.getProperty(attr))
      }
    } else {
      Field sourceField = ReflectionUtils.findField(destination.getClass(), propName)
      def destinationClass = sourceField.getGenericType()
      destination.setProperty(attribute.key, bind(prop, destinationClass))
    }
  }
  
  
  def processEnum(Object source,  Object destination,  def attribute, Map entities){
    def prop = source.getProperty(attribute.key)
    def propName = attribute.key
    
    if (entities[attribute.key]) {
      entities[attribute.key].each { attr ->
        destination.setProperty(attribute.key + attr.toString().capitalize(), prop?."${attr}")
      }
    } else {
      Field sourceField = ReflectionUtils.findField(destination.getClass(), propName)
      def destinationClass = sourceField.getGenericType()
      destination.setProperty(attribute.key, bind(prop, destinationClass))
    }
  }
  
  
  def processCollection(Object source,  Object destination,  def attribute, Map entities){
    def destList = []
    def prop = source.getProperty(attribute.key)
    def propName = attribute.key

    def listItem = prop.find { it != null }

    if (listItem) {

      Field sourceField = ReflectionUtils.findField(destination.getClass(), propName)
      def destinationClass
      if (sourceField?.getGenericType()?.actualTypeArguments?.length > 0) {
        destinationClass = sourceField?.getGenericType()?.actualTypeArguments[0]
      }

      if (destinationClass) {
        prop.each { theItem ->
          if (theItem != null) {
            destList << bind(theItem, destinationClass)
          }
        }
      }

    }
    if (destList) {
      destination.setProperty(attribute.key, destList)
    }
  }

  def processSimpleProperty(Object source,  Object destination,  def attribute, Map entities){
    if (attribute.value != null) {
      destination.setProperty(attribute.key, attribute.value)
    }
  }
  
  def getDynamicBindingValue(Object source,  Object destination,  def attribute){
    def value
    if(dynamicBindings){
      def customClosure = dynamicBindings.find{
        it.source.name == source.class.name
        it.destination.name == destination.class.name
      }?.customBindings?.get(attribute.key)

      value = customClosure ? customClosure(attribute.value) : null
    }
    value
  }


  def static getSourceProperties(Object source, Set<String> destinationsProps){
    Map<String, Object> props = [:]
    if(DomainClassArtefactHandler.isDomainClass(source.class)) {
      def d = new DefaultGrailsDomainClass(source.class)
      props = ( d.properties.toList() << [ name:'id' ]).collectEntries {
        [ ( it.name ): source."${it.name}" ]
      }
    } else {
      props  = source.properties
    }
    props.findAll { it.key in  destinationsProps}
  }

  private Map getValidDestinationProperties(Object destination, List invalidFields, Object source) {
    Set<String> destinationsProps = destination.properties.keySet().findAll { !(it in invalidFields) }

    Map entities = [:].withDefault { [] }

    destinationsProps.each {
      String[] entity = StringUtils.splitByCharacterTypeCamelCase(it)

      if (entity.length > 0) {
        def entitiName = entity.inject("") { acc, val ->
          if (checkForEntity(source, acc + val)) {
            acc + val
          } else if (checkForEntity(source, acc)) {
            String value = it.split("$acc")[1]
            value = value.replaceFirst(value[0], value[0].toLowerCase())
            entities."$acc" << value
            acc
          } else {
            acc = acc + val
            acc
          }
        }
      } else {
        if (checkForEntity(it)) {
          entities."$it"
        }
      }
    }

    def result = [
      destinationsProps: destinationsProps,
      entities: entities
    ]
    result
  }
  
  private static boolean checkForEntity(Object source, String val) {
    source.hasProperty(val) && (DomainClassArtefactHandler.isDomainClass(source.getProperty(val).getClass()) ||
      source.getProperty(val).getClass().isEnum())
  }
  
}

