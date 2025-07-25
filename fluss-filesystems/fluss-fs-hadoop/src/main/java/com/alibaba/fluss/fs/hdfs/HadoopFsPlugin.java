/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.fluss.fs.hdfs;

import com.alibaba.fluss.config.Configuration;
import com.alibaba.fluss.fs.FileSystem;
import com.alibaba.fluss.fs.FileSystemPlugin;
import com.alibaba.fluss.fs.UnsupportedFileSystemSchemeException;
import com.alibaba.fluss.fs.hdfs.utils.HadoopUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.UnknownHostException;

import static com.alibaba.fluss.utils.Preconditions.checkArgument;
import static com.alibaba.fluss.utils.Preconditions.checkNotNull;

/* This file is based on source code of Apache Flink Project (https://flink.apache.org/), licensed by the Apache
 * Software Foundation (ASF) under the Apache License, Version 2.0. See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership. */

/**
 * A file system plugin for Hadoop-based file systems.
 *
 * <p>This plugin calls Hadoop's mechanism to find a file system implementation for a given file
 * system scheme (a {@link org.apache.hadoop.fs.FileSystem}) and wraps it as a Fluss file system (a
 * {@link FileSystem}).
 */
public class HadoopFsPlugin implements FileSystemPlugin {

    public static final String SCHEME = "hdfs";

    private static final Logger LOG = LoggerFactory.getLogger(HadoopFsPlugin.class);

    @Override
    public String getScheme() {
        return SCHEME;
    }

    @Override
    public FileSystem create(URI fsUri, Configuration flussConfig) throws IOException {
        checkNotNull(fsUri, "fsUri");

        final String scheme = fsUri.getScheme();
        checkArgument(scheme != null, "file system has null scheme");

        // from here on, we need to handle errors due to missing optional
        // dependency classes
        try {
            // -- (1) get the Hadoop config

            final org.apache.hadoop.conf.Configuration hadoopConfig =
                    HadoopUtils.getHadoopConfiguration(flussConfig);

            // -- (2) get the Hadoop file system class for that scheme

            final Class<? extends org.apache.hadoop.fs.FileSystem> fsClass;
            try {
                fsClass = org.apache.hadoop.fs.FileSystem.getFileSystemClass(scheme, hadoopConfig);
            } catch (IOException e) {
                throw new UnsupportedFileSystemSchemeException(
                        "Hadoop File System abstraction does not support scheme '"
                                + scheme
                                + "'. "
                                + "Either no file system implementation exists for that scheme, "
                                + "or the relevant classes are missing from the classpath.",
                        e);
            }

            // -- (3) instantiate the Hadoop file system

            LOG.debug(
                    "Instantiating for file system scheme {} Hadoop File System {}",
                    scheme,
                    fsClass.getName());

            final org.apache.hadoop.fs.FileSystem hadoopFs = fsClass.newInstance();

            // -- (4) create the proper URI to initialize the file system

            final URI initUri;
            if (fsUri.getAuthority() != null) {
                initUri = fsUri;
            } else {
                if (LOG.isDebugEnabled()) {
                    LOG.debug(
                            "URI {} does not specify file system authority, trying to load default authority (fs.defaultFS)",
                            fsUri);
                }

                String configEntry = hadoopConfig.get("fs.defaultFS", null);
                if (configEntry == null) {
                    // fs.default.name deprecated as of hadoop 2.2.0 - see
                    // http://hadoop.apache.org/docs/current/hadoop-project-dist/hadoop-common/DeprecatedProperties.html
                    configEntry = hadoopConfig.get("fs.default.name", null);
                }

                if (LOG.isDebugEnabled()) {
                    LOG.debug("Hadoop's 'fs.defaultFS' is set to {}", configEntry);
                }

                if (configEntry == null) {
                    throw new IOException(
                            getMissingAuthorityErrorPrefix(fsUri)
                                    + "Hadoop configuration did not contain an entry for the default file system ('fs.defaultFS').");
                } else {
                    try {
                        initUri = URI.create(configEntry);
                    } catch (IllegalArgumentException e) {
                        throw new IOException(
                                getMissingAuthorityErrorPrefix(fsUri)
                                        + "The configuration contains an invalid file system default name "
                                        + "('fs.default.name' or 'fs.defaultFS'): "
                                        + configEntry);
                    }

                    if (initUri.getAuthority() == null) {
                        throw new IOException(
                                getMissingAuthorityErrorPrefix(fsUri)
                                        + "Hadoop configuration for default file system ('fs.default.name' or 'fs.defaultFS') "
                                        + "contains no valid authority component (like hdfs namenode, S3 host, etc)");
                    }
                }
            }

            // -- (5) configure the Hadoop file system

            try {
                hadoopFs.initialize(initUri, hadoopConfig);
            } catch (UnknownHostException e) {
                String message =
                        "The Hadoop file system's authority ("
                                + initUri.getAuthority()
                                + "), specified by either the file URI or the configuration, cannot be resolved.";

                throw new IOException(message, e);
            }

            return new HadoopFileSystem(hadoopFs);
        } catch (ReflectiveOperationException | LinkageError e) {
            throw new UnsupportedFileSystemSchemeException(
                    "Cannot support file system for '"
                            + fsUri.getScheme()
                            + "' via Hadoop, because Hadoop is not in the classpath, or some classes "
                            + "are missing from the classpath.",
                    e);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Cannot instantiate file system for URI: " + fsUri, e);
        }
    }

    private static String getMissingAuthorityErrorPrefix(URI fsURI) {
        return "The given file system URI ("
                + fsURI.toString()
                + ") did not describe the authority "
                + "(like for example HDFS NameNode address/port or S3 host). "
                + "The attempt to use a configured default authority failed: ";
    }
}
