package net.medievalrp.spyglass.plugin.network;

import java.util.UUID;

record FlushRequest(long id, UUID requestId, String senderId) {
}
