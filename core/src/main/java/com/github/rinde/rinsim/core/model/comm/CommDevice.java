/*
 * Copyright (C) 2011-2015 Rinde van Lon, iMinds-DistriNet, KU Leuven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.rinde.rinsim.core.model.comm;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Verify.verifyNotNull;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.github.rinde.rinsim.geom.Point;
import com.google.common.base.MoreObjects;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;

/**
 * A communication device that can be used to communicate with other
 * {@link CommUser}s. Instances can be constructed via {@link CommDeviceBuilder}
 * . A communication device has two important properties, reliability and range.
 * For more information regarding this properties see
 * {@link #send(MessageContents, CommUser)} and
 * {@link #broadcast(MessageContents)}.
 * @author Rinde van Lon
 */
public final class CommDevice {
  private final CommModel model;
  private final CommUser user;
  private final double reliability;
  private final Optional<Double> maxRange;
  private final Predicate<CommUser> rangePredicate;

  private final List<Message> unreadMessages;
  private final List<Message> outbox;

  CommDevice(CommDeviceBuilder builder) {
    model = builder.model;
    user = builder.user;
    reliability = builder.deviceReliability;
    maxRange = builder.deviceMaxRange;
    if (maxRange.isPresent()) {
      rangePredicate = new RangePredicate(user, maxRange.get());
    } else {
      rangePredicate = Predicates.alwaysTrue();
    }
    unreadMessages = new ArrayList<>();
    outbox = new ArrayList<>();
    model.addDevice(this, user);
  }

  /**
   * Retrieves the unread messages that this device has received. Calling this
   * method will clear the unread messages of this device.
   * @return An immutable list of {@link Message}s.
   */
  public ImmutableList<Message> getUnreadMessages() {
    final ImmutableList<Message> msgs = ImmutableList
        .copyOf(unreadMessages);
    unreadMessages.clear();
    return msgs;
  }

  /**
   * @return The reliability of this device for sending and receiving messages.
   */
  public double getReliability() {
    return reliability;
  }

  /**
   * @return The maximum range for sending messages, or
   *         {@link Optional#absent()} if the device as unlimited range.
   */
  public Optional<Double> getMaxRange() {
    return maxRange;
  }

  /**
   * Attempts to send a message with the specified contents to the specified
   * recipient. The actual sending of a message is done at the end of the
   * current tick. Based on the range and reliability it is determined whether
   * the message will be send.
   * <p>
   * <b>Reliability</b> If this device has a reliability of <code>p</code> there
   * is a probability of <code>1-p</code> that the message will not be send. If
   * the receiving device has a reliability of <code>r</code> there is a
   * probability of <code>1-r</code> that the message will not be received at
   * the other end. This means that in practice the probability of a successful
   * delivery is <code>(1-p) * (1-r)</code>.
   * <p>
   * <b>Range</b> If this device has a maximum range the message will only be
   * delivered if the recipient is within that range <i>at the moment of sending
   * at the end of the tick</i>. Note that the range only influences sending of
   * messages, it is possible to receive messages from senders that are outside
   * of its max range.
   * @param contents The contents to send as part of the message.
   * @param recipient The recipient of the message.
   */
  public void send(MessageContents contents, CommUser recipient) {
    checkArgument(user != recipient,
        "Can not send message to self %s.",
        recipient);
    checkArgument(model.contains(recipient),
        "%s can not send message to unknown recipient: %s.",
        user, recipient);
    outbox.add(Message.createDirect(user, recipient, contents, rangePredicate));
  }

  /**
   * Attempts to broadcast a message with the specified contents. The actual
   * sending of a message is done at the end of the current tick. Based on the
   * range and reliability it is determined to whom a message will be send.
   * <p>
   * <b>Reliability</b> If this device has a reliability of <code>p</code> there
   * is a probability of <code>1-p</code> that the message will not be send to a
   * particular receiver. If the receiving device has a reliability of
   * <code>r</code> there is a probability of <code>1-r</code> that the message
   * will not be received at the other end. This means that in practice the
   * probability of a successful delivery is <code>(1-p) * (1-r)</code>.
   * <p>
   * <b>Range</b> If this device has a maximum range the message will only be
   * delivered to the recipients that are within that range <i>at the moment of
   * sending at the end of the tick</i>. Note that the range only influences
   * sending of messages, it is possible to receive messages from senders that
   * are outside of its max range.
   * @param contents The contents to send as part of the message.
   */
  public void broadcast(MessageContents contents) {
    outbox.add(Message.createBroadcast(user, contents, rangePredicate));
  }

  void receive(Message m) {
    unreadMessages.add(m);
  }

  void sendMessages() {
    for (final Message msg : outbox) {
      model.send(msg, reliability);
    }
    outbox.clear();
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper("CommDevice")
        .add("owner", user)
        .add("reliability", reliability)
        .add("range", maxRange)
        .toString();
  }

  static CommDeviceBuilder builder(CommModel m, CommUser u) {
    return new CommDeviceBuilder(m, u);
  }

  static class RangePredicate implements Predicate<CommUser> {
    private final CommUser user;
    private final double range;

    RangePredicate(CommUser u, double r) {
      user = u;
      range = r;
    }

    @Override
    public boolean apply(@Nullable CommUser input) {
      return Point.distance(user.getPosition(),
          verifyNotNull(input).getPosition()) <= range;
    }
  }
}
