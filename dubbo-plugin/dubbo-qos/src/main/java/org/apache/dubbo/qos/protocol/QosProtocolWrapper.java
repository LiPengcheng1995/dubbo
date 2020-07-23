/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.dubbo.qos.protocol;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.UrlUtils;
import org.apache.dubbo.qos.common.QosConstants;
import org.apache.dubbo.qos.server.Server;
import org.apache.dubbo.rpc.Exporter;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Protocol;
import org.apache.dubbo.rpc.ProtocolServer;
import org.apache.dubbo.rpc.RpcException;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.apache.dubbo.common.constants.QosConstants.ACCEPT_FOREIGN_IP;
import static org.apache.dubbo.common.constants.QosConstants.QOS_ENABLE;
import static org.apache.dubbo.common.constants.QosConstants.QOS_HOST;
import static org.apache.dubbo.common.constants.QosConstants.QOS_PORT;

// 这个 Protocol 的封装是针对 Registry 的 Protocol 的增强
//
// QOS 全称 Quality of Service ，为了提升服务质量增加的功能支持，我的理解，就是监听一个端口，接收外面
// 来的 http/telnet 消息，进行服务的上下线动态调整。（有点像jsf控制台的操作）
// http://dubbo.apache.org/zh-cn/blog/introduction-to-dubbo-qos.html
public class QosProtocolWrapper implements Protocol {

    private final Logger logger = LoggerFactory.getLogger(QosProtocolWrapper.class);

    private static AtomicBoolean hasStarted = new AtomicBoolean(false);

    private Protocol protocol;

    public QosProtocolWrapper(Protocol protocol) {
        if (protocol == null) {
            throw new IllegalArgumentException("protocol == null");
        }
        this.protocol = protocol;
    }

    @Override
    public int getDefaultPort() {
        return protocol.getDefaultPort();
    }

    @Override
    public <T> Exporter<T> export(Invoker<T> invoker) throws RpcException {
        if (UrlUtils.isRegistry(invoker.getUrl())) {
            // registry 的 url 才增强
            //
            // 因为动态调整只涉及对注册中心的上下线，所以如果没有注册中心用直连的话，这里就没必要启动了
            startQosServer(invoker.getUrl());
            return protocol.export(invoker);
        }
        return protocol.export(invoker);
    }

    @Override
    public <T> Invoker<T> refer(Class<T> type, URL url) throws RpcException {
        if (UrlUtils.isRegistry(url)) {
            startQosServer(url);
            return protocol.refer(type, url);
        }
        return protocol.refer(type, url);
    }

    @Override
    public void destroy() {
        protocol.destroy();
        stopServer();
    }

    @Override
    public List<ProtocolServer> getServers() {
        return protocol.getServers();
    }

    private void startQosServer(URL url) {
        try {
            // 如果这个服务已经启动，就不用操作
            if (!hasStarted.compareAndSet(false, true)) {
                return;
            }

            boolean qosEnable = url.getParameter(QOS_ENABLE, true);
            if (!qosEnable) {
                logger.info("qos won't be started because it is disabled. " +
                        "Please check dubbo.application.qos.enable is configured either in system property, " +
                        "dubbo.properties or XML/spring-boot configuration.");
                return;
            }

            // 参数同步到 QOS 的 server 中，然后启动一个 netty 的 server
            String host = url.getParameter(QOS_HOST);
            int port = url.getParameter(QOS_PORT, QosConstants.DEFAULT_PORT);
            boolean acceptForeignIp = Boolean.parseBoolean(url.getParameter(ACCEPT_FOREIGN_IP, "false"));
            Server server = Server.getInstance();
            server.setHost(host);
            server.setPort(port);
            server.setAcceptForeignIp(acceptForeignIp);
            server.start();

        } catch (Throwable throwable) {
            logger.warn("Fail to start qos server: ", throwable);
        }
    }

    /*package*/ void stopServer() {
        if (hasStarted.compareAndSet(true, false)) {
            Server server = Server.getInstance();
            server.stop();
        }
    }
}
