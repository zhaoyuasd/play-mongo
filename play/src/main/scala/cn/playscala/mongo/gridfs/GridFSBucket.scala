package cn.playscala.mongo.gridfs

import java.io.{File, FileInputStream, InputStream}
import cn.playscala.mongo.MongoDatabase
import com.mongodb.async.SingleResultCallback
import com.mongodb.async.client.gridfs.helpers.AsyncStreamHelper.toAsyncInputStream
import com.mongodb.async.client.gridfs.{GridFSBuckets, GridFSBucket => JGridFSBucket}
import com.mongodb.session.ClientSession
import org.bson.BsonString
import org.bson.types.ObjectId
import scala.concurrent.Future
import cn.playscala.mongo.internal.AsyncResultHelper._
import play.api.libs.json.{JsObject, JsString, Json}
import scala.concurrent.ExecutionContext.Implicits.global
import cn.playscala.mongo._

/**
  * A factory for GridFSBucket instances.
  *
  * @since 1.2
  */
object GridFSBucket {

  /**
    * Create a new GridFS bucket with the default `'fs'` bucket name
    *
    * @param database the database instance to use with GridFS
    * @return the GridFSBucket
    */
  def apply(database: MongoDatabase): GridFSBucket = new GridFSBucket(GridFSBuckets.create(database.wrapped))

  /**
    * Create a new GridFS bucket with a custom bucket name
    *
    * @param database   the database instance to use with GridFS
    * @param bucketName the custom bucket name to use
    * @return the GridFSBucket
    */
  def apply(database: MongoDatabase, bucketName: String): GridFSBucket = new GridFSBucket(GridFSBuckets.create(database.wrapped, bucketName))
}

class GridFSBucket(val wrapped: JGridFSBucket) {

  /**
    * Finds all documents in the files collection.
    *
    * @return the GridFS find iterable interface
    * @see [[http://docs.mongodb.org/manual/tutorial/query-documents/ Find]]
    */
  def find(): GridFSFindBuilder = GridFSFindBuilder(wrapped.find(), this)

  /**
    * Finds all documents in the collection that match the filter.
    *
    * Below is an example of filtering against the filename and some nested metadata that can also be stored along with the file data:
    *
    * `
    * Filters.and(Filters.eq("filename", "mongodb.png"), Filters.eq("metadata.contentType", "image/png"));
    * `
    *
    * @param filter the query filter
    * @return the GridFS find iterable interface
    * @see com.mongodb.client.model.Filters
    */
  def find(filter: JsObject): GridFSFindBuilder = GridFSFindBuilder(wrapped.find(filter), this)

  def findById(fileId: String): Future[Option[GridFSFile]] = GridFSFindBuilder(wrapped.find(Json.obj("_id" -> fileId)), this).first

  def deleteById(fileId: String): Future[Void] = toFuture(wrapped.delete(JsString(fileId), _: SingleResultCallback[Void]))

  /**
    * Upload a file to GridFS
    *
    * @param file   the file to upload
    * @return the file_id
    */
  def uploadFromFile(file: File): Future[String] = {
    uploadFromFile(None, None, file.getName, "application/octet-stream", file, GridFSUploadOptions())
  }

  /**
    * Upload a file to GridFS
    *
    * @param file   the file to upload
    * @param fileName the file name
    * @return the file_id
    */
  def uploadFromFile(fileName: String, file: File): Future[String] = {
    uploadFromFile(None, None, fileName, "application/octet-stream", file, GridFSUploadOptions())
  }

  /**
    * Upload a file to GridFS
    *
    * @param file   the file to upload
    * @param fileName file name
    * @param contentType content type
    * @return the file_id
    */
  def uploadFromFile(fileName: String, contentType: String, file: File): Future[String] = {
    uploadFromFile(None, None, fileName, contentType, file, GridFSUploadOptions())
  }

  /**
    * Upload a file to GridFS
    *
    * @param fileName file name
    * @param contentType content type
    * @param file   the file to upload
    * @param options   upload options
    * @return the file id
    */
  def uploadFromFile(fileName: String, contentType: String, file: File, options: GridFSUploadOptions): Future[String] = {
    uploadFromFile(None, None, fileName, contentType, file, options)
  }

  /**
    * Upload a file to GridFS
    *
    * @param file   the file to upload
    * @return the file_id
    */
  def uploadFromFile(clientSession: ClientSession, file: File): Future[String] = {
    uploadFromFile(Some(clientSession), None, file.getName, "application/octet-stream", file, GridFSUploadOptions())
  }

  /**
    * Upload a file to GridFS
    *
    * @param file   the file to upload
    * @param fileName the file name
    * @return the file_id
    */
  def uploadFromFile(clientSession: ClientSession, fileName: String, file: File): Future[String] = {
    uploadFromFile(Some(clientSession), None, fileName, "application/octet-stream", file, GridFSUploadOptions())
  }

  /**
    * Upload a file to GridFS
    *
    * @param file   the file to upload
    * @param fileName file name
    * @param contentType content type
    * @return the file_id
    */
  def uploadFromFile(clientSession: ClientSession, fileName: String, contentType: String, file: File): Future[String] = {
    uploadFromFile(Some(clientSession), None, fileName, contentType, file, GridFSUploadOptions())
  }

  /**
    * Upload a file to GridFS
    *
    * @param fileName file name
    * @param contentType content type
    * @param file   the file to upload
    * @param options   upload options
    * @return the file id
    */
  def uploadFromFile(clientSession: ClientSession, fileName: String, contentType: String, file: File, options: GridFSUploadOptions): Future[String] = {
    uploadFromFile(Some(clientSession), None, fileName, contentType, file, options)
  }

  /**
    * Upload a file to GridFS
    *
    * @param clientSession   the client session with which to associate this operation
    * @param fileId   the custom id of the file
    * @param fileName   the custom name of the file
    * @param file   the file to upload
    * @param options  the GridFSUploadOptions
    * @return the file_id
    */
  def uploadFromFile(clientSession: Option[ClientSession], fileId: Option[String], fileName: String, contentType: String, file: File, options: GridFSUploadOptions): Future[String] = {
    val file_id = fileId.getOrElse(ObjectId.get().toHexString)
    options.wrapped.getMetadata.put("contentType", contentType)

    toFuture[Void](clientSession match {
      case Some(cs) =>
        wrapped.uploadFromStream(cs, new BsonString(file_id), fileName, toAsyncInputStream(new FileInputStream(file)), options.wrapped, _: SingleResultCallback[Void])
      case None =>
        wrapped.uploadFromStream(new BsonString(file_id), fileName, toAsyncInputStream(new FileInputStream(file)), options.wrapped, _: SingleResultCallback[Void])
    }).map(_ => file_id)
  }

  /**
    * Upload to GridFS from an input stream
    *
    * @param inputStream   the input stream to upload
    * @return the file_id
    */
  def uploadFromInputStream(fileName: String, inputStream: InputStream): Future[String] = {
    uploadFromInputStream(None, None, fileName, "application/octet-stream", inputStream, GridFSUploadOptions())
  }

  /**
    * Upload to GridFS from an input stream
    *
    * @param inputStream   the input stream to upload
    * @param contentType  the content type of the file
    * @return the file_id
    */
  def uploadFromInputStream(fileName: String, contentType: String, inputStream: InputStream): Future[String] = {
    uploadFromInputStream(None, None, fileName, contentType, inputStream, GridFSUploadOptions())
  }

  /**
    * Upload to GridFS from an input stream
    *
    * @param inputStream   the input stream to upload
    * @param contentType  the content type of the file
    * @param options  the GridFSUploadOptions
    * @return the file_id
    */
  def uploadFromInputStream(fileName: String, contentType: String, inputStream: InputStream, options: GridFSUploadOptions): Future[String] = {
    uploadFromInputStream(None, None, fileName, contentType, inputStream, options)
  }

  /**
    * Upload to GridFS from an input stream
    *
    * @param inputStream   the input stream to upload
    * @return the file_id
    */
  def uploadFromInputStream(clientSession: ClientSession, fileName: String, inputStream: InputStream): Future[String] = {
    uploadFromInputStream(Some(clientSession), None, fileName, "application/octet-stream", inputStream, GridFSUploadOptions())
  }

  /**
    * Upload to GridFS from an input stream
    *
    * @param inputStream   the input stream to upload
    * @param contentType  the content type of the file
    * @return the file_id
    */
  def uploadFromInputStream(clientSession: ClientSession, fileName: String, contentType: String, inputStream: InputStream): Future[String] = {
    uploadFromInputStream(Some(clientSession), None, fileName, contentType, inputStream, GridFSUploadOptions())
  }

  /**
    * Upload to GridFS from an input stream
    *
    * @param inputStream   the input stream to upload
    * @param contentType  the content type of the file
    * @param options  the GridFSUploadOptions
    * @return the file_id
    */
  def uploadFromInputStream(clientSession: ClientSession, fileName: String, contentType: String, inputStream: InputStream, options: GridFSUploadOptions): Future[String] = {
    uploadFromInputStream(Some(clientSession), None, fileName, contentType, inputStream, options)
  }

  /**
    * Upload to GridFS from an input stream
    *
    * @param clientSession   the client session with which to associate this operation
    * @param fileId   the custom id of the file
    * @param fileName   the custom name of the file
    * @param inputStream   the input stream to upload
    * @param options  the GridFSUploadOptions
    * @return the file_id
    */
  def uploadFromInputStream(clientSession: Option[ClientSession], fileId: Option[String], fileName: String, contentType: String, inputStream: InputStream, options: GridFSUploadOptions): Future[String] = {
    val file_id = fileId.getOrElse(ObjectId.get().toHexString)
    options.wrapped.getMetadata.put("contentType", contentType)

    toFuture[Void](clientSession match {
      case Some(cs) =>
        wrapped.uploadFromStream(cs, new BsonString(file_id), fileName, toAsyncInputStream(inputStream), options.wrapped, _: SingleResultCallback[Void])
      case None =>
        wrapped.uploadFromStream(new BsonString(file_id), fileName, toAsyncInputStream(inputStream), options.wrapped, _: SingleResultCallback[Void])
    }).map(_ => file_id)
  }

  /**
    * Opens a GridFSDownloadStreamIterator from which the application can read the contents of the stored file specified by {@code id}.
    *
    * @param id the custom id value of the file, to be put into a stream.
    * @return the stream
    */
  def openDownloadStream(id: String) = new GridFSDownloadStream(wrapped.openDownloadStream(new BsonString(id)))

}
