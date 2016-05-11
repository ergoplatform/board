package services
import scala.concurrent.{Future, Promise}
import scala.concurrent.ExecutionContext.Implicits.global

trait Subscription {
  // the index is the subscriptionId
  // the value is the reference
  private var subscriptionMap = Map[String, String]()
  
  def addSubscription(subscriptionId: String, reference: String) = {
    subscriptionMap.synchronized {
      subscriptionMap += (subscriptionId -> reference)
    }
  }
  
  def getSubscription(subscriptionId: String) : Option[String] = {
    subscriptionMap.synchronized {
      subscriptionMap.get(subscriptionId)
    }
  }
  
  def removeSubscription(subscriptionId: String, reference: String) : Future[Unit] = {
    val promise = Promise[Unit]()
    Future {
      subscriptionMap.synchronized {
        subscriptionMap.get(subscriptionId) match {
          case Some(ref) =>
            if(ref == reference) {
              subscriptionMap -= subscriptionId
              promise.success({})
            } else {
              promise.failure(new Error(s"error: reference ${reference} does not match ${ref}"))
            }
          case None =>
            promise.failure(new Error(s"Error: subscription id not found: $subscriptionId"))
        }
      }
    }
    promise.future
  }
}