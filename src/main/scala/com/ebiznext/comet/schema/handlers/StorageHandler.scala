/*
 *
 *  * Licensed to the Apache Software Foundation (ASF) under one or more
 *  * contributor license agreements.  See the NOTICE file distributed with
 *  * this work for additional information regarding copyright ownership.
 *  * The ASF licenses this file to You under the Apache License, Version 2.0
 *  * (the "License"); you may not use this file except in compliance with
 *  * the License.  You may obtain a copy of the License at
 *  *
 *  *    http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 *
 */

package com.ebiznext.comet.schema.handlers

import java.time.{Instant, LocalDateTime, ZoneId}

import org.apache.commons.io.IOUtils
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs._
import org.apache.spark.sql.execution.streaming.FileStreamSource.Timestamp

/**
  * Interface required by any filesystem manager
  */
trait StorageHandler {

  def move(src: Path, dst: Path): Boolean

  def delete(path: Path): Boolean

  def exist(path: Path): Boolean

  def mkdirs(path: Path): Boolean

  def copyFromLocal(source: Path, dest: Path): Unit

  def moveFromLocal(source: Path, dest: Path): Unit

  def read(path: Path): String

  def write(data: String, path: Path): Unit

  def list(path: Path, extension: String = "", since: LocalDateTime = LocalDateTime.MIN): List[Path]

  def blockSize(path: Path): Long

  def contentSummary(path: Path): ContentSummary

  def lastModified(path: Path): Timestamp

  def spaceConsumed(path: Path): Long

}

/**
  * HDFS Filesystem Handler
  */
class HdfsStorageHandler extends StorageHandler {

  /**
    * Read a UTF-8 text file into a string used to load yml configuration files
    *
    * @param path : Absolute file path
    * @return file content as a string
    */
  def read(path: Path): String = {
    val conf = new Configuration()
    val fs = FileSystem.get(conf)
    val stream = fs.open(path)
    val content = IOUtils.toString(stream, "UTF-8")
    content
  }

  /**
    * Write a string to a UTF-8 text file. Used for yml configuration files.
    *
    * @param data file content as a string
    * @param path : Absolute file path
    */
  def write(data: String, path: Path): Unit = {
    val conf = new Configuration()
    val fs = FileSystem.get(conf)
    fs.delete(path, false)
    val os: FSDataOutputStream = fs.create(path)
    os.writeBytes(data)
    os.close()
  }

  /**
    * List all files in folder
    *
    * @param path      Absolute folder path
    * @param extension : Files should end with this string. To list all files, simply provide an empty string
    * @param since     Minimum modification time of liste files. To list all files, simply provide the beginning of all times
    * @return List of Path
    */
  def list(path: Path, extension: String, since: LocalDateTime): List[Path] = {
    val conf = new Configuration()
    val fs = FileSystem.get(conf)
    val iterator: RemoteIterator[LocatedFileStatus] = fs.listFiles(path, false)
    iterator
      .filter { status =>
        val time = LocalDateTime.ofInstant(
          Instant.ofEpochMilli(status.getModificationTime),
          ZoneId.systemDefault
        )
        time.isAfter(since) && status.getPath().getName().endsWith(extension)
      }
      .map(status => status.getPath())
      .toList
  }

  /**
    * Move file
    *
    * @param path source path (file or folder)
    * @param dest destination path (file or folder)
    * @return
    */
  override def move(path: Path, dest: Path): Boolean = {
    val conf = new Configuration()
    val fs = FileSystem.get(conf)
    FileUtil.copy(fs, path, fs, dest, true, true, conf)
  }

  /**
    * delete file (skip trash)
    *
    * @param path : Absolute path of file to delete
    */
  override def delete(path: Path): Boolean = {
    val conf = new Configuration()
    val fs = FileSystem.get(conf)
    fs.delete(path, true)
  }

  /**
    * Create folder if it does not exsit including any intermediary non existent folder
    *
    * @param path Absolute path of folder to create
    */
  override def mkdirs(path: Path): Boolean = {
    val conf = new Configuration()
    val fs = FileSystem.get(conf)
    fs.mkdirs(path)
  }

  /**
    * Copy file from local filesystem to target file system
    *
    * @param source Local file path
    * @param dest   destination file path
    */
  override def copyFromLocal(source: Path, dest: Path): Unit = {
    val conf = new Configuration()
    val fs = FileSystem.get(conf)
    fs.copyFromLocalFile(source, dest)
  }

  /**
    * Move file from local filesystem to target file system
    *
    * @param source Local file path
    * @param dest   destination file path
    */
  override def moveFromLocal(source: Path, dest: Path): Unit = {
    val conf = new Configuration()
    val fs = FileSystem.get(conf)
    fs.moveFromLocalFile(source, dest)
  }

  override def exist(path: Path): Boolean = {
    val conf = new Configuration()
    val fs = FileSystem.get(conf)
    fs.exists(path)
  }

  def blockSize(path: Path): Long = {
    val conf = new Configuration()
    val fs = FileSystem.get(conf)
    fs.getDefaultBlockSize(path)
  }

  def contentSummary(path: Path): ContentSummary = {
    val conf = new Configuration()
    val fs = FileSystem.get(conf)
    fs.getContentSummary(path)
  }

  def spaceConsumed(path: Path): Long = {
    contentSummary(path).getSpaceConsumed
  }

  def lastModified(path: Path): Timestamp = {
    val conf = new Configuration()
    val fs = FileSystem.get(conf)
    fs.getFileStatus(path).getModificationTime
  }
}

object HdfsStorageHandler extends HdfsStorageHandler
