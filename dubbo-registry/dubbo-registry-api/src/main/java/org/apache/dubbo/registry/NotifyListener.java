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
package org.apache.dubbo.registry;

import org.apache.dubbo.common.URL;

import java.util.List;

/**
 * NotifyListener. (API, Prototype, ThreadSafe)
 *
 * @see org.apache.dubbo.registry.RegistryService#subscribe(URL, NotifyListener)
 */
public interface NotifyListener {

    /**
     * Triggered when a service change notification is received.
     * <p>
     * Notify needs to support the contract: <br>
     * 1. Always notifications on the service interface and the dimension of the data type. that is, won't notify part of the same type data belonging to one service. Users do not need to compare the results of the previous notification.<br>
     * 2. The first notification at a subscription must be a full notification of all types of data of a service.<br>
     * 3. At the time of change, different types of data are allowed to be notified separately, e.g.: providers, consumers, routers, overrides. It allows only one of these types to be notified, but the data of this type must be full, not incremental.<br>
     * 4. If a data type is empty, need to notify a empty protocol with category parameter identification of url data.<br>
     * 5. The order of notifications to be guaranteed by the notifications(That is, the implementation of the registry). Such as: single thread push, queue serialization, and version comparison.<br>
     *
     * @param urls The list of registered information , is always not empty. The meaning is the same as the return value of {@link org.apache.dubbo.registry.RegistryService#lookup(URL)}.
     */
    // 当收到一个服务改变的通知时，会调用这个方法。
    // 此接口的实现类要实现以下功能：
    // 1. 变动的通知都是接口纬度和数据类型纬度，（是比较大块的），而不是改了哪一个小地方就通知哪个小地方，用户也不用拿到变更的内容后和之前版本的状态做对比（直接覆盖就好）
    // 2. 订阅后的第一次通知必须是全量的通知【一共有多少生产/消费，都是啥，最全的通知】
    // 3. 出现改变时，可以根据改变的内容分别通知对应的模块，比如：生产者、消费者、路由之类的。但是每个模块的通知，必须是全的（此处参考第一点）
    // 4. 即使一个数据类型是空的，也要通知一个空的 protocol 。（意思是一定要通知，不能私吞变动。这样能统一出一个兜底的空操作，该报警报警，该干啥干啥）
    // 5. 通知的先后顺序和注册中心通知的顺序必须保持严格一致（通过 FIFO 队列、对比版本号、单线程慢慢跑 ，怎么整都行，但是一定要保证着）
    //
    // 入参的url，是注册中心传来的信息，总是非空，每个 url 和 {@link org.apache.dubbo.registry.RegistryService#lookup(URL)} 反查的拿到的结果一样
    void notify(List<URL> urls);

}