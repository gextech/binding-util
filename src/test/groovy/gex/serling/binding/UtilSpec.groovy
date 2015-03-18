package gex.serling.binding

import gex.serling.binding.domain.Enemy
import gex.serling.binding.domain.Status
import gex.serling.binding.domain.Superpower
import gex.serling.binding.domain.Hero as DomainHero
import gex.serling.binding.dto.Hero
import spock.lang.Specification
import org.springframework.boot.test.IntegrationTest
import org.springframework.boot.test.SpringApplicationContextLoader
import org.springframework.test.context.ContextConfiguration
import org.springframework.transaction.annotation.Transactional
import spock.lang.Unroll

/**
 * Created by domix on 12/26/14.
 */
@IntegrationTest
@ContextConfiguration(loader = SpringApplicationContextLoader, classes = TestApplication)
@Transactional
class UtilSpec extends Specification {


  def 'should bind a new Instance taking a instanciated object'() {
    when:
      def object = Util.bind(new Hero(name: 'The Doctor'), Hero)
    then:
      object.name == 'The Doctor'
  }
  
  def 'Bind between an entity object and a pojo is made without problem'(){
    given:
      gex.serling.binding.domain.Hero domain = new gex.serling.binding.domain.Hero(name: 'Dalek Caan')
      println domain
      domain.save(flush: true)

    when:
      Hero dto = Util.bind(domain, Hero.class)
      println dto.properties

    then:
      dto.name == domain.name
  }

  def 'it is binding all properties except the explicit avoided ones'(){
    given:
      Hero dto = new Hero()
      dto.name = "The doctor"
      dto.age = 904
      dto.isInmortal = true
      dto.otherNames = ['Jonh Smith', 'Doctor who?']

    when:
      gex.serling.binding.domain.Hero domain = Util.bind(dto, gex.serling.binding.domain.Hero.class, ['notPersistedField', 'isInmortal'])

    then:
      domain.name == dto.name
      domain.age == dto.age
      domain.isInmortal == null
  }


  def 'It binds embeddedObjects using camelCase'(){
    given:
      gex.serling.binding.domain.Hero domainHero = new gex.serling.binding.domain.Hero()
      domainHero.name = "The doctor"
      domainHero.age = 904
      domainHero.isInmortal = true
      domainHero.superpower = new Superpower(name: 'Regeneration')

    when:
      Hero dtoHero = Util.bind(domainHero, Hero.class)

    then:
      dtoHero.name == domainHero.name
      dtoHero.superpowerName == domainHero.superpower.name
  }


  def 'It binds enums properties correctly'(){
    given:
      gex.serling.binding.domain.Hero domainHero = new gex.serling.binding.domain.Hero()
      domainHero.name = "The doctor"
      domainHero.age = 904
      domainHero.isInmortal = true
      domainHero.superpower = new Superpower(name: 'Regeneration')
      domainHero.status = Status.DELETED

    when:
      Hero dtoHero = Util.bind(domainHero, Hero.class)

    then:
      dtoHero.name == domainHero.name
      dtoHero.superpowerName == domainHero.superpower.name
      dtoHero.statusId == Status.DELETED.id
  }
  
  def 'It binds also properties that are lists'(){
    given:
      gex.serling.binding.domain.Hero domainHero = new gex.serling.binding.domain.Hero()
      domainHero.name = "The doctor"
      domainHero.enemies = [new Enemy(name: 'Dalek'), new Enemy(name: 'Cyberman'), new Enemy(name: 'Weeping Angel')]

    when:
      Hero dtoHero = Util.bind(domainHero, Hero.class)

    then:
      dtoHero.name == domainHero.name
      dtoHero.enemies*.name.containsAll(domainHero.enemies*.name)
  }

  @Unroll
  def 'It correctly binds  booleans >> When #booleanValue'() {
    given:
      gex.serling.binding.domain.Hero domainHero = new gex.serling.binding.domain.Hero()
      domainHero.name = "The Doctor"
      domainHero.isInmortal = booleanValue

    when:
      Hero dtoHero = Util.bind(domainHero, Hero.class)

    then:
      dtoHero.name == domainHero.name
      dtoHero.isInmortal == bindedBooleanValue

    where:
      booleanValue || bindedBooleanValue
      null         || null
      false        || false
      true         || true
  }

  def 'It binds CamelCase properties, no matter they are not embedded objects'(){
    given:
      gex.serling.binding.domain.Hero domainHero = new gex.serling.binding.domain.Hero()
      domainHero.name = "The doctor"
      domainHero.enemies = [new Enemy(name: 'Dalek'), new Enemy(name: 'Cyberman'), new Enemy(name: 'Weeping Angel')]

    when:
      Hero dtoHero = Util.bind(domainHero, Hero.class)

    then:
      dtoHero.name == domainHero.name
      dtoHero.enemies.each{
        domainHero.enemies*.name.contains(it.name)
      }
  }

  def 'It can be specified a dynamic way to bind properties (simple properties)'(){
    when:
      def util = new Util()

      Map map = [
        "age" : { x -> x * 10 }
      ]

      def cb = new DynamicMapping(sourceClass: Hero.class, destinationClass: gex.serling.binding.domain.Hero.class, customBindings: map )

      util.registerBinding( cb )

      def object = util.dynamicBind(new Hero(name: 'Goku', age: 21 ), gex.serling.binding.domain.Hero)
    then:

      object.name == 'Goku'
      object.age == 210
  }

  def 'It can be specified a dynamic way to bind properties (collection properties)'(){
    given:
      def util = new Util()

      def hardcodedEnemies = [new Enemy(name: 'OtroDale'), new Enemy(name: 'OtroCyberman'), new Enemy(name: 'Otro Weeping Ange')]
      Map map = [
        "enemies" : { x -> hardcodedEnemies }
      ]

      def cb = new DynamicMapping(sourceClass: gex.serling.binding.domain.Hero.class, destinationClass: Hero.class , customBindings: map )

      util.registerBinding( cb )

      gex.serling.binding.domain.Hero domainHero = new gex.serling.binding.domain.Hero()
      domainHero.name = "The doctor"
      domainHero.enemies = [new Enemy(name: 'Dalek'), new Enemy(name: 'Cyberman'), new Enemy(name: 'Weeping Angel')]

    when:
      Hero dtoHero = util.dynamicBind(domainHero, Hero.class)

    then:
      dtoHero.name == domainHero.name
      dtoHero.enemies.containsAll(hardcodedEnemies)
  }

  def 'A configured instance is used for multiple bindings'(){
    given:
      def util = new Util()

      // Register bindings
      def hardcodedEnemies = [new Enemy(name: 'Silence'), new Enemy(name: 'Dark')]

      Map mappings = [
        "age" : { x, y -> x * 10 },
        "enemies" : { x -> hardcodedEnemies },
        "separatedByCommaEnemies" : {val, obj -> obj.enemies*.name.join(",")},
        "lastName": {val, obj, extra ->  extra[obj.name] }
      ]

      def db = new DynamicMapping(
        sourceClass: gex.serling.binding.domain.Hero.class,
        destinationClass: Hero.class,
        customBindings: mappings,
        exclusions: ["notPersistedField", "isInmortal"])

      util.registerBinding( db )

      // Aux map
      Map extraParams = ['The doctor': 'Smith', 'Pikachu': 'Mon' ]

    when: 'A binding'
      gex.serling.binding.domain.Hero domainHero = new gex.serling.binding.domain.Hero()
      domainHero.name = "The doctor"
      domainHero.enemies = [new Enemy(name: 'Dalek'), new Enemy(name: 'Cyberman'), new Enemy(name: 'Weeping Angel')]
      domainHero.age = 94
      domainHero.isInmortal = true
      domainHero.status = Status.ACTIVE
    
      Hero dtoHero = util.dynamicBind(domainHero, Hero.class, extraParams)

    then:
      dtoHero.name == "The doctor"
      dtoHero.lastName == 'Smith'
      dtoHero.enemies.containsAll(hardcodedEnemies)
      dtoHero.age == 940
      dtoHero.statusId == Status.ACTIVE.id
      dtoHero.isInmortal == null
      dtoHero.notPersistedField == null
      dtoHero.separatedByCommaEnemies == "Dalek,Cyberman,Weeping Angel"
      

    when: 'A second binding'
      domainHero = new gex.serling.binding.domain.Hero()
      domainHero.name = "Pikachu"
      dtoHero.lastName == 'Mon'
      domainHero.enemies = [new Enemy(name: 'Jessy'), new Enemy(name: 'James')]
      domainHero.age = 5
      domainHero.isInmortal = false
      domainHero.status = Status.SUSPENDED

      dtoHero = util.dynamicBind(domainHero, Hero.class, extraParams)

    then:
      dtoHero.name == "Pikachu"
      dtoHero.enemies.containsAll(hardcodedEnemies)
      dtoHero.age == 50
      dtoHero.statusId == Status.SUSPENDED.id
      dtoHero.isInmortal == null
      dtoHero.notPersistedField == null
      dtoHero.separatedByCommaEnemies == "Jessy,James"
  }

  def 'should override a property when bind with null value'() {
    when:
      DomainHero hero = new DomainHero(name: "lkasjfdljaskdfj")
      hero = Util.bind(new Hero(name: null), hero)
    then:
      hero.name == null
  }

}
