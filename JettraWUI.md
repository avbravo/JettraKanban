# JettraWUI

## Backlog

## To Do

## In Progress
### @ModelToRecordConversor
<!-- jettra-meta created-by="avbravo" created-at="2026-05-26T09:19:59.995198611" sprint-id="53d40c97-9c59-470a-9ac0-378d1200b3d8" -->
@ModelToRecordConversor(goal=)

La anotación ModelToRecordConversor  es una anotación creada mediante Java Annotation procesing un conversor de datos de Model a Record de manera de evitar al usuario el proceso de conversión entre un model y un record. Si no se especifica el goal se asume que es el mismo nombre del model eliminando Model.java.
Se usa en conjunto de la anotación
@PropertiesInRecord(field=””)
Esta anotación le indica a un record que atributos corresponden en el model, si no se especifica el campo field se asume que el model usa el mismo nombre de atributo.

Ejemplo
Clase Record
public record Persona(String id, String name)

La Clase Model

@JettraViewModel
@ModelToRecordConversor(goal=Persona.java)
public class PersonaModel{
     @PropertiesInRecord(field="id")
      private String id;
     @PropertiesInRecord(field="name")
     private String nombre;
}

Genera en tiempo de compilacion  la clase PersonaRecordModelConverter que contendrá el código migrate
public PersonaRecordModelConverter{


import jettra.scoped.ApplicationScoped;

@ApplicationScoped
public class PersonaConversor {

    /**
     * Convierte de Persona (Record) a PersonaModel
     */
    public PersonaModel toModel(Persona record) {
        if (record == null) {
            return null;
        }
        
        PersonaModel model = new PersonaModel();
        model.setId(record.id());
        model.setNombre(record.name()); 
        
        return model;
    }

    /**
     * Convierte de PersonaModel a Persona (Record)
     */
    public Persona toRecord(PersonaModel model) {
        if (model == null) {
            return null;
        }
        
        return new Persona(
            model.getId(),
            model.getNombre() 
        );
    }
}






Crea en JettraWUI la documentacion en /guide/recordtomodel.md y modelproperties.md y añade ejemplos.

### @CrudView con Controller
<!-- jettra-meta created-by="avbravo" created-at="2026-05-25T15:15:30.355376462" sprint-id="53d40c97-9c59-470a-9ac0-378d1200b3d8" -->
Añadir a la anotacion @CrudView la integracion con un enpoint mediante
Controller, es decir la funcionalidad es similar a la obtenida con un Repository
pero en este caso seria mediante un endpoint que esta definido en el parametro 
Controller de CrudView.
Actualiza la documentacion en JettraWUI/guide/crudview.md


## Review

## Done

