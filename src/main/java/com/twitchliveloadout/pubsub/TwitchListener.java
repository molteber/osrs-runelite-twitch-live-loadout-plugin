package com.twitchliveloadout.pubsub;

public interface TwitchListener {
    void rewardRedeemed(String redeemName, String message);
}
