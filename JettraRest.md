# JettraRest

## Backlog

## To Do

## In Progress

## Review
### Error en AutorController
<!-- jettra-meta created-by="avbravo" created-at="2026-05-26T14:16:46.460833929" sprint-id="e4248b34-13d1-4802-8404-bb8b4d271ef4" -->
En la clase AutorController.java 
public class AuthorController {
en el metodo
public Response save(AuthorModel model) {
        AuthorRepository.save(model);
        return new Response(200, "Saved successfully");
    }
envia el mensaje 
constructor Response in class Response cannot be applied to given types;
  required: int,Object,Map<String,String>
  found:    int,String
  reason: actual and formal argument lists differ in length


## Done

