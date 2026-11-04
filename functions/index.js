const admin = require("firebase-admin");
const { onDocumentCreated } = require("firebase-functions/v2/firestore");

admin.initializeApp();

exports.deliverUserNotification = onDocumentCreated(
  "users/{userId}/notifications/{notificationId}",
  async (event) => {
    const notification = event.data?.data();
    if (!notification) return;

    const tokenSnapshot = await admin
      .firestore()
      .collection("users")
      .doc(event.params.userId)
      .collection("fcmTokens")
      .get();
    const tokenDocuments = tokenSnapshot.docs.filter(
      (document) => typeof document.data().token === "string"
    );

    if (tokenDocuments.length === 0) return;

    const data = {
      notificationId: event.params.notificationId,
      recipientId: event.params.userId,
      type: String(notification.type || "activity"),
      title: String(notification.actorName || "Talky"),
      body: String(notification.message || "You have a new notification"),
      storyId: String(notification.storyId || ""),
      commentId: String(notification.commentId || ""),
      conversationId: String(notification.conversationId || ""),
    };

    for (let index = 0; index < tokenDocuments.length; index += 500) {
      const page = tokenDocuments.slice(index, index + 500);
      const response = await admin.messaging().sendEachForMulticast({
        tokens: page.map((document) => document.data().token),
        data,
        android: { priority: "high" },
      });

      const invalidTokens = [];
      response.responses.forEach((result, responseIndex) => {
        const code = result.error?.code;
        if (
          code === "messaging/invalid-registration-token" ||
          code === "messaging/registration-token-not-registered"
        ) {
          invalidTokens.push(page[responseIndex].ref.delete());
        } else if (!result.success) {
          console.error("FCM delivery failed", {
            notificationId: event.params.notificationId,
            tokenId: page[responseIndex].id,
            code,
          });
        }
      });

      await Promise.all(invalidTokens);
    }
  }
);
