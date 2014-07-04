package com.example

import spray.routing.authentication.UserPass
import akka.actor.{ActorRef, Actor, ActorLogging, Props}
import org.apache.shiro.crypto.SecureRandomNumberGenerator
import org.apache.shiro.util.ByteSource
import org.apache.shiro.crypto.hash.Sha512Hash
import spray.httpx.SprayJsonSupport
import spray.json.DefaultJsonProtocol
import akka.persistence.{Persistent, View, EventsourcedProcessor}
import java.util.Date
import scala.util.Failure

object SecurityService {
  // business types
  case class User(username: String, password: String) {
    require(!username.isEmpty, "username is mandatory")
    require(!password.isEmpty, "password is mandatory")
  }

  object JsonMarshaller extends DefaultJsonProtocol with SprayJsonSupport {
    implicit val secServiceUserFormat = jsonFormat2(SecurityService.User)
    implicit val secServiceViewUserFormat = jsonFormat1(SecurityServiceView.User)
  }

  // commands
  case class DeleteUserByName(username: String)
  case class AddUser(user: SecurityService.User)
  case class Authenticate(userPass: Option[UserPass])

  // events
  case class UserCreated(date: Date, user: SecurityService.User)

  def props = Props(new SecurityService)
}

class SecurityService extends EventsourcedProcessor with ActorLogging {
  import SecurityService._

  override def processorId: String = "SecurityService"

  def handleCreateUser(event: UserCreated): ActorRef = {
    val UserCreated(_, User(username, _)) = event
    context.actorOf(AppUser.props(username), username)
  }

  def handleCreateUserAndSendCommand(event: UserCreated) {
    val UserCreated(_, user) = event
    handleCreateUser(event) ! AppUser.CreateUser(user)
  }

  override def receiveRecover: Receive = {
    case evt: UserCreated => handleCreateUser(evt)
  }

  override def receiveCommand: Receive = {
    case msg @ Authenticate(userPass) => userPass match {
      case Some(up) => context.child(up.user) match {
        case Some(user) => user forward msg
        case None => sender ! None
      }
      case None => sender ! None
    }

    case DeleteUserByName(username) =>
      context.child(username) match {
        case Some(user) => user ! AppUser.Delete
        case None => sender ! Failure
      }

    case AddUser(user) => context.child(user.username) match {
      case Some(userActor) => userActor ! AppUser.UpdateUser(user)
      case None => persist(SecurityService.UserCreated(new Date(), user))(handleCreateUserAndSendCommand)
    }
  }
}

object AppUser {
  // commands
  case object Delete
  case class UpdateUser(user: SecurityService.User)
  case class CreateUser(user: SecurityService.User)

  // events
  case class Deleted(date: Date, username: String)
  case class UserCreated(date: Date, user: SecurityService.User)
  case class UserUpdated(date: Date, user: SecurityService.User)

  def props(username: String) = Props(new AppUser(username))

  def generateSalt: String = {
    val rng = new SecureRandomNumberGenerator
    val byteSourceSalt: ByteSource = rng.nextBytes
    byteSourceSalt.toHex
  }

  def generateHashedPassword(passwordText: String, passwordSalt: String, iterations: Int = 512) =  new Sha512Hash(passwordText, passwordSalt, iterations).toHex

  def generatePassword(username: String, password: String): (String, String) = {
    val passwordSalt = generateSalt
    val hashedPassword = generateHashedPassword(password, passwordSalt)
    (hashedPassword, passwordSalt)
  }
}

class AppUser(username: String) extends EventsourcedProcessor with ActorLogging {
  import AppUser._
  override def processorId: String = username
  log.info("Creating AppUser: {}", username)

  var updated: Date = _
  var deleted = false
  var passwordHash: String = _
  var passwordSalt: String = _

  def updateUser(date: Date, password: String) {
    log.info("Updating user")
    val generatedPassword = generatePassword(username, password)
    updated = date
    passwordHash = generatedPassword._1
    passwordSalt = generatedPassword._2
  }

  def handleUserCreated(evt: AppUser.UserCreated) {
    val AppUser.UserCreated(date, SecurityService.User(_, password)) = evt
    updateUser(date, password)
  }
  
  def handleUserUpdated(evt: AppUser.UserUpdated) {
    val AppUser.UserUpdated(date, SecurityService.User(_, password)) = evt
    updateUser(date, password)
  }
  
  def handleDeleted(evt: AppUser.Deleted) {
    val AppUser.Deleted(date, _) = evt
    updated = date
    deleted = true
  }
  
  override def receiveRecover: Receive = {
    case evt: AppUser.Deleted => handleDeleted(evt)
    case evt: AppUser.UserCreated => handleUserCreated(evt)
    case evt: AppUser.UserUpdated => handleUserUpdated(evt)
  }

  override def receiveCommand: Receive = {
    case AppUser.Delete => persist(AppUser.Deleted(new Date, username))(handleDeleted)
    case AppUser.CreateUser(user) => persist(AppUser.UserCreated(new Date, user))(handleUserCreated)
    case AppUser.UpdateUser(user) => persist(AppUser.UserUpdated(new Date, user))(handleUserUpdated)
    case SecurityService.Authenticate(userPass) =>
      sender ! userPass.flatMap { up =>
        log.info("Authenticating: {}", up.user)
        val hashedPwd = generateHashedPassword(up.pass, passwordSalt)
        if(hashedPwd == passwordHash) Some(up.user) else None
      }
  }
}

object SecurityServiceView {
  // business types
  case class User(username: String)

  // commands
  case object GetAllUsers
  case class GetUserByName(username: String)

  def props = Props(new SecurityServiceView)
}

class SecurityServiceView extends View with ActorLogging {
  import SecurityServiceView._
  log.info("Creating SecurityServiceView")
  override def processorId: String = "SecurityService"
  override def viewId: String = "SecurityServiceView"

  var users = List.empty[SecurityServiceView.User]

  override def receive: Actor.Receive = {
    case Persistent(SecurityService.UserCreated(date, user), _) =>
      log.info("Creating UserView: {}", user.username)
      context.actorOf(Props(new UserView(user.username)), user.username)
      users = SecurityServiceView.User(user.username) :: users
    case Persistent(AppUser.UserCreated(_, _), _) =>
    case Persistent(AppUser.UserUpdated(_, _), _) =>
    case Persistent(AppUser.Deleted(_, username), _) =>
      log.info("Deleting user: {}", username)
      users = users.filterNot { user => user.username.equals(username) }
    case GetAllUsers => sender ! users
    case GetUserByName(username) => sender ! users.find { user => user.username.equals(username) }
    case msg @ _ => log.warning("Could not handle message: {}", msg)
  }
}

class UserView(username: String) extends View with ActorLogging {
  log.info("Creating UserView: {}", username)
  override def processorId: String = username

  override def receive: Actor.Receive = {
    case msg @ _ =>
      log.info("Forwarding message: {}", msg)
      context.parent forward msg
  }
}