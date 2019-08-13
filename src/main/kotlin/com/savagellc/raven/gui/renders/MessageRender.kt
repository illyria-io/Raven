package com.savagellc.raven.gui.renders

import com.savagellc.raven.core.CoreManager
import com.savagellc.raven.discord.ImageCache
import com.savagellc.raven.gui.MessageMenu
import com.savagellc.raven.gui.cursourOnHover
import com.savagellc.raven.include.GuiMessage
import javafx.application.Platform
import javafx.concurrent.Task
import javafx.concurrent.Worker
import javafx.embed.swing.SwingFXUtils
import javafx.geometry.Insets
import javafx.scene.control.Label
import javafx.scene.control.ListView
import javafx.scene.image.ImageView
import javafx.scene.input.MouseButton
import javafx.scene.web.WebView;
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import org.json.JSONObject
import java.lang.ref.SoftReference

const val maxImageWidth = 500.0

private fun getLabel(content:String, style:String = "", isUnderLined:Boolean = false): Label {
    val label = Label(content)
    label.isWrapText = true;
    if(style.isNotEmpty()) label.style = style
    label.isUnderline = isUnderLined
    return label
}
private fun addUserImage(
    id: String,
    avatar: String,
    rootBox: HBox,
    messagesList: ListView<HBox>
) {
    val task = object : Task<Void>() {
        override fun call(): Void? {
            val image = SwingFXUtils.toFXImage(ImageCache.getImage("https://cdn.discordapp.com/avatars/$id/$avatar"), null)
           Platform.runLater {
               val view = ImageView(image)
               view.isPreserveRatio = true
               view.fitWidth = 25.0
               rootBox.children.add(0, view)
               messagesList.refresh()
           }
            return null
        }
    }
    val loader = Thread(task)
    loader.isDaemon = true
    loader.start()
}
fun render(
    message: GuiMessage,
    messagesList: ListView<HBox>,
    coreManager: CoreManager,
    renderSeparator: Boolean
): Triple<HBox, Label, VBox> {
    val rootBox = HBox()

    if(renderSeparator) {
        rootBox.style = "-fx-border-color: red; -fx-border-width: 2 0 0 0"
    }
    rootBox.setOnMouseClicked {
            MessageMenu.openMenu(message, it.screenX, it.screenY, coreManager, messagesList, it.button == MouseButton.SECONDARY)
    }
    rootBox.maxWidth = maxImageWidth
    val contentRow = VBox()
    contentRow.padding = Insets(2.0, 0.0, 2.0, 5.0)
    rootBox.prefWidth = messagesList.width
    messagesList.widthProperty().addListener { observable, oldValue, newValue ->
        if(newValue.toDouble() < maxImageWidth)
        rootBox.prefWidth = newValue.toDouble()
    }
    if(message.author.get("avatar") is String)
    Platform.runLater {
        addUserImage(message.author.getString("id"), message.author.getString("avatar"), rootBox, messagesList)
    }
    HBox.setHgrow(contentRow, Priority.ALWAYS)
    val nameLabel = getLabel(message.senderName, "-fx-font-size: 16;")
    contentRow.children.add(nameLabel)
        val contentLabel = getLabel(message.content, "-fx-font-size: 15;")
    if(message.content != "") {
        contentRow.children.add(contentLabel)
    }
    if(message.type == 3) {
        val contentLabel = getLabel("> Started call")
        contentRow.children.add(contentLabel)
    }
    message.attachments.forEach {
        it as JSONObject
        val childBox = VBox()
        childBox.padding = Insets(5.0, 5.0, 5.0, 10.0)
        if(it.has("title")) childBox.children.add(getLabel(it.getString("title"), "-fx-font-size: 15;", true))
        if(it.has("description"))  childBox.children.add(getLabel(it.getString("description")))
        if(it.has("url")) {
            val task = object : Task<Void>() {
                override fun call(): Void? {
                    val url = it.getString("url")
                    val lW = it.getInt("width").toDouble()
                    val imageView = ImageView(SwingFXUtils.toFXImage(ImageCache.getImage(url), null))
                    imageView.isPreserveRatio = true
                    Platform.runLater {
                        imageView.fitWidth = if(messagesList.width < lW) messagesList.width else if (lW <= maxImageWidth) lW else  maxImageWidth
                        messagesList.widthProperty().addListener { observable, oldValue, newValue ->
                            if(newValue.toDouble() <= lW && newValue.toDouble() <= maxImageWidth)
                                imageView.fitWidth = newValue.toDouble()
                        }
                        childBox.children.add(imageView)

                        messagesList.refresh()
                    }
                    return null
                }
            }
            val thread = Thread(task)
            thread.isDaemon = true
            thread.start()
        }
        contentRow.children.add(childBox)
    }
    message.embeds.forEach {
        it as JSONObject
        val childBox = VBox()
        childBox.padding = Insets(5.0, 5.0, 5.0, 10.0)
        var previewIndex = -1
        childBox.padding = Insets(5.0, 5.0, 5.0, 10.0)
        if(it.has("title")) childBox.children.add(getLabel(it.getString("title"), "-fx-font-size: 15;", true))
        if(it.has("description"))  childBox.children.add(getLabel(it.getString("description")))
        if(it.has("video") && !it.isNull("video")) {
            var webView:WebView? = null
            var imageView:ImageView? = null
            cursourOnHover(childBox)
            var switched = false
           childBox.setOnMouseClicked { ev ->
              if(webView != null && ev.pickResult.intersectedNode == webView) return@setOnMouseClicked
               if(ev.button == MouseButton.PRIMARY) {
                   if(switched) {
                       childBox.children[previewIndex] = imageView!!
                       webView!!.engine.load(null)
                       switched = false
                       return@setOnMouseClicked
                   } else {
                       imageView = childBox.children[previewIndex] as ImageView
                       if(webView != null) {
                           webView!!.engine.reload()
                       } else {
                            val renderer = WebView()
                           renderer.prefWidth = imageView!!.fitWidth
                           renderer.prefHeight = 430.0
                           renderer.engine.load(it.getJSONObject("video").getString("url"))
                           renderer.engine.loadWorker.stateProperty().addListener { observable, oldValue, newValue ->
                               if(newValue == Worker.State.SUCCEEDED) {
                                   renderer.engine.executeScript("document.querySelector(\".ytp-cued-thumbnail-overlay-image\").click()")
                               }
                           }
                           webView = renderer
                       }
                       childBox.children[previewIndex] = webView
                       switched = true
                   }
               }
           }
        }
        if(it.has("thumbnail")) {
            val task = object : Task<Void>() {
                override fun call(): Void? {
                    val url = it.getJSONObject("thumbnail").getString("url")
                    val imageView = ImageView(SwingFXUtils.toFXImage(ImageCache.getImage(url), null))
                    val lW = it.getJSONObject("thumbnail").getInt("width").toDouble()
                    imageView.isPreserveRatio = true
                    Platform.runLater {
                        imageView.fitWidth = if(messagesList.width < lW) messagesList.width else if (lW <= maxImageWidth) lW else  maxImageWidth
                        messagesList.widthProperty().addListener { observable, oldValue, newValue ->
                            if(newValue.toDouble() <= lW && newValue.toDouble() <= maxImageWidth)
                                imageView.fitWidth = newValue.toDouble()
                        }
                        childBox.children.add(imageView)
                        previewIndex = childBox.children.indexOf(imageView)
                        messagesList.refresh()

                    }
                    return null
                }
            }
            val thread = Thread(task)
            thread.isDaemon = true
            thread.start()
        }
        contentRow.children.add(childBox)
    }
    rootBox.children.add(contentRow)

    return Triple(rootBox, contentLabel, contentRow)
}