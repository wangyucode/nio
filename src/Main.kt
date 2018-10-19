import java.io.IOException
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.charset.Charset
import kotlin.system.exitProcess


fun main(args: Array<String>) {
//    val server = NioServer(1080)
//    Thread(server).start()


    val msg = "GET / HTTP/1.1\n" +
            "cache-control: no-cache\n" +
            "User-Agent: PostmanRuntime/7.3.0\n" +
            "Accept: */*\n" +
            "Host: baidu.com\n" +
            "accept-encoding: gzip, deflate\n" +
            "Connection: keep-alive\n" +
            "\n"

    val client = NioClient("baidu.com", 80, msg)

    Thread(client).start()
}

class NioServer(port: Int) : Runnable {
    var started: Boolean
    private val selector: Selector
    private val serverChannel: ServerSocketChannel

    init {
        try {
            //创建选择器
            selector = Selector.open()
            //打开监听通道
            serverChannel = ServerSocketChannel.open()
            //开启非阻塞模式
            serverChannel.configureBlocking(false)
            //绑定端口
            serverChannel.socket().bind(InetSocketAddress(port))
            //监听客户端连接请求
            serverChannel.register(selector, SelectionKey.OP_ACCEPT)
            //标记服务器已开启
            started = true
            System.out.println("服务器已启动在：$port 端口")
        } catch (e: IOException) {
            e.printStackTrace()
            exitProcess(1)
        }
    }

    fun stop() {
        started = false
    }

    override fun run() {

        while (started) {
            //1s超时时间,超时后会结束阻塞，被唤醒
            selector.select(1000)
            val keys = selector.selectedKeys()
            val iterator = keys.iterator()
            while (iterator.hasNext()) {
                val key = iterator.next()
                handleInput(key)
                iterator.remove()
            }
        }
        selector.close()
        serverChannel.close()
        println("服务器已停止")
    }

    private fun handleInput(key: SelectionKey) {
        if (key.isValid) {
            if (key.isAcceptable) {
                val serverSocketChannel = key.channel() as ServerSocketChannel
                //通过ServerSocketChannel的accept创建SocketChannel实例
                //完成该操作意味着完成TCP三次握手，TCP物理链路正式建立
                val socketChannel = serverSocketChannel.accept()
                //设置为非阻塞的
                socketChannel.configureBlocking(false)
                //注册为读
                socketChannel.register(selector, SelectionKey.OP_READ)
            } else if (key.isReadable) {
                val socketChannel = key.channel() as SocketChannel
                //创建ByteBuffer，并开辟一个1M的缓冲区
                val buffer = ByteBuffer.allocate(1024)
                //读取请求码流，返回读取到的字节数
                while (socketChannel.read(buffer) > 0) {
                    //将缓冲区设置为position=0，limit=readBytes-1，用于后续对缓冲区的读取操作
                    buffer.flip()
                    //根据缓冲区可读字节数创建字节数组
                    val bytes = ByteArray(buffer.remaining())
                    //将缓冲区可读字节数组复制到新建的数组中
                    buffer.get(bytes)
                    println(String(bytes, Charset.forName("utf-8")))
                    buffer.clear()
                }

                key.cancel()
                socketChannel.close()
                println("客户端消息已读取完毕")
            }
        }
    }

}


class NioClient(host: String, port: Int, val msg: String) : Runnable {
    var started: Boolean
    private val selector: Selector
    private val socketChannel: SocketChannel

    init {
        try {
            //创建选择器
            selector = Selector.open()
            //打开监听通道
            socketChannel = SocketChannel.open()
            //开启非阻塞模式
            socketChannel.configureBlocking(false)
            //连接服务器
            socketChannel.connect(InetSocketAddress(host, port))
            //注册连接事件
            socketChannel.register(selector, SelectionKey.OP_CONNECT)
            //标记信道已打开
            started = true
        } catch (e: IOException) {
            e.printStackTrace()
            exitProcess(1)
        }
    }

    fun stop() {
        started = false
    }

    override fun run() {
        while (started) {
            //1s超时时间,超时后会结束阻塞，被唤醒
            selector.select(1000)
            val keys = selector.selectedKeys()
            val iterator = keys.iterator()
            while (iterator.hasNext()) {
                val key = iterator.next()
                handleInput(key)
                iterator.remove()
            }

            println(socketChannel.isConnected)
        }
        selector.close()
        socketChannel.close()
        println("客户端连接已关闭")
    }


    private fun handleInput(key: SelectionKey) {
        if (key.isValid) {
            val sc = key.channel() as SocketChannel

            if (key.isConnectable) {
                if (sc.finishConnect()) {
                    socketChannel.register(selector, SelectionKey.OP_READ)
                    //将消息编码为字节数组
                    val bytes = msg.toByteArray()
                    //根据数组容量创建ByteBuffer
                    val writeBuffer = ByteBuffer.allocate(bytes.size)
                    //将字节数组复制到缓冲区
                    writeBuffer.put(bytes)
                    //flip操作
                    writeBuffer.flip()
                    //发送缓冲区的字节数组
                    socketChannel.write(writeBuffer)
                } else {
                    println("没连上")
                    stop()
                }
            } else if (key.isReadable) {
                //创建ByteBuffer，并开辟一个1M的缓冲区
                val buffer = ByteBuffer.allocate(1024)
                //读取请求码流，返回读取到的字节数
                while (socketChannel.read(buffer) > 0) {
                    //将缓冲区设置为position=0，limit=readBytes-1，用于后续对缓冲区的读取操作
                    buffer.flip()
                    if (buffer.remaining() == 0) {
                        continue
                    }
                    //根据缓冲区可读字节数创建字节数组
                    val bytes = ByteArray(buffer.remaining())
                    //将缓冲区可读字节数组复制到新建的数组中
                    buffer.get(bytes)
                    println(String(bytes, Charset.forName("utf-8")))
                    buffer.clear()
                }

                key.cancel()
                println("服务器消息已读取完毕${socketChannel.isConnected}")
                stop()
            }
        }
    }
}