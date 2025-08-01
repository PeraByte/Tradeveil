/**
 * Firebase Functions v2 (Modular SDK)
 * Docs: https://firebase.google.com/docs/functions
 */
const {onRequest} = require("firebase-functions/v2/https");
const {onDocumentCreated} = require("firebase-functions/v2/firestore");
const {logger} = require("firebase-functions");
const {initializeApp} = require("firebase-admin/app");
const {getFirestore, FieldValue} = require("firebase-admin/firestore");

initializeApp();

// Existing HTTP Trigger
exports.helloWorld = onRequest((request, response) => {
  logger.log("HelloWorld function called!");
  response.json({
    message: "Hello from Firebase!",
    timestamp: new Date().toISOString(),
  });
});

// Existing Firestore Trigger (unchanged)
exports.onUserCreated = onDocumentCreated("users/{userId}", (event) => {
  const userData = event.data.data();
  logger.log(`New user created: ${userData.email}`, event.params.userId);
});

// Existing HTTP Function (unchanged)
exports.getUserData = onRequest({cors: true}, async (request, response) => {
  try {
    if (!request.auth) {
      response.status(401).json({error: "Unauthorized"});
      return;
    }
    const userId = request.auth.uid;
    logger.log(`Fetching data for user: ${userId}`);
    response.json({
      userId,
      profile: {
        name: "John Doe",
        premium: true,
      },
    });
  } catch (error) {
    logger.error("Error in getUserData:", error);
    response.status(500).json({error: "Internal server error"});
  }
});

// NEW: Team Count Increment Function
exports.updateTeamCount = onDocumentCreated(
    "users/{userId}/confirmedReferrals/{referralId}",
    async (event) => {
      const {userId} = event.params;
      const db = getFirestore();

      try {
        await db.runTransaction(async (transaction) => {
          const userRef = db.collection("users").doc(userId);
          transaction.update(userRef, {
            teamCount: FieldValue.increment(1),
            lastTeamUpdate: FieldValue.serverTimestamp(),
          });
        });
        logger.log(`Team count updated for user: ${userId}`);
      } catch (error) {
        logger.error("Error updating team count:", error);
        throw new Error("Failed to update team count");
      }
    },
);
