module.exports = {
  content: [
    "../src/main/resources/templates/**/*.html",
    "../src/main/resources/static/**/*.js"
  ],
  theme: { extend: {} },
  plugins: [require("daisyui")],
  daisyui: { themes: ["light", "dark", "cupcake"] }
};