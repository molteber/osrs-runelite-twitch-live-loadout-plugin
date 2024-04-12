package com.twitchliveloadout.pubsub;

import com.twitchliveloadout.pubsub.messages.MessageData;

public class Message<T extends MessageData> {
    public String type;
    public T data;

    public Message(String type)
    {
        this.type = type;
    }

    public Message(String type, T data)
    {
        this.type = type;
        this.data = data;
    }
}
