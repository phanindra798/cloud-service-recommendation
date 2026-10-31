import { detectAnswers, pendingQuestions } from "./questions";

test("finds user counts written in different ways", () => {
  expect(detectAnswers("a chat app for 50k users").users).toBe(50000);
  expect(detectAnswers("expecting 2 million monthly active users").users).toBe(2000000);
  expect(detectAnswers("around 5 lakh customers").users).toBe(500000);
  expect(detectAnswers("for 12,000 students").users).toBe(12000);
  expect(detectAnswers("1.5M subscribers").users).toBe(1500000);
  expect(detectAnswers("an app with 3 microservices").users).toBeNull();
});

test("finds the country or region", () => {
  expect(detectAnswers("food delivery app in Bangalore").region).toBe("INDIA");
  expect(detectAnswers("serving customers across Europe").region).toBe("EUROPE");
  expect(detectAnswers("a startup in the UK").region).toBe("UK");
  expect(detectAnswers("a todo app").region).toBeNull();
});

test("does not match a region inside another word", () => {
  // "eu" appears inside "museum", "us" inside "business"
  expect(detectAnswers("a museum business website").region).toBeNull();
});

test("finds the kind of app", () => {
  expect(detectAnswers("an online shop for shoes").appType).toBe("ECOMMERCE");
  expect(detectAnswers("real-time chat for gamers").appType).toBe("CHAT");
  expect(detectAnswers("a video streaming platform").appType).toBe("VIDEO_STREAMING");
  expect(detectAnswers("something cool").appType).toBeNull();
});

test("asks only what the description leaves out", () => {
  expect(pendingQuestions("something cool").questions).toEqual(["region", "users", "appType", "highAvailability"]);

  const { questions, known } = pendingQuestions("a chat app for 50k users in India");
  expect(questions).toEqual(["highAvailability"]);
  expect(known).toEqual({ region: "INDIA", users: 50000, appType: "CHAT" });
});
