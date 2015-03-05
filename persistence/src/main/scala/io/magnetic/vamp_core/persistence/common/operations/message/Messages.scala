package io.magnetic.vamp_core.persistence.common.operations.message

import io.magnetic.vamp_core.model.artifact.Breed

trait Request {
  
}

trait BreedRequest extends Request {

}

trait Response {

}


object Messages {
  case class BreedOps[A <: BreedRequest](request: A)
  case class SaveBreed(entity: Breed) extends BreedRequest
  case class GetBreed(name: String) extends BreedRequest
  case class ListBreeds(pageNumber: Int = 1, perPage: Int = 10) extends BreedRequest
  case class DeleteBreed(name: String) extends BreedRequest
  case object DispatchError extends Response
  case object OperationSuccess extends Response
  case object NotFound extends Response
}
