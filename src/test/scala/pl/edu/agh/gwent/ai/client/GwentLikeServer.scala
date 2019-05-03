package pl.edu.agh.gwent.ai.client

import java.net.InetSocketAddress

import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import com.avsystem.commons.collection.CollectionAliases._

//unsafe, dirty implementation, just to get tests running
class GwentLikeServer(
  address: InetSocketAddress,
  val queue: MMap[Int, List[String]] = MMap.empty
) extends WebSocketServer(address) {
  private[this] var connectionNum: Int = 0

  override def onOpen(conn: WebSocket, handshake: ClientHandshake): Unit = {
    val conNum = connectionNum
    connectionNum += 1
    conn.setAttachment(conNum)
    queue.update(conNum, Nil)
  }

  override def onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean): Unit = {
    queue.remove(conn.getAttachment[Int])
  }

  override def onMessage(conn: WebSocket, message: String): Unit = {
    val messages = queue.apply(conn.getAttachment[Int])
    queue.update(conn.getAttachment[Int], message :: messages)
  }

  override def onError(conn: WebSocket, ex: Exception): Unit = {
    println(s"Server failed with $ex")
    if (conn ne null) queue.remove(conn.getAttachment[Int])
  }

  override def onStart(): Unit = {}

  def sendGwentMessage(con: WebSocket, event: String, message: String): Unit = {
    con.send(s"""42["$event",$message]""")
  }
}
