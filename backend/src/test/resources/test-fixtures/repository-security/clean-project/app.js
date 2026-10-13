// Clean benign application source file
const express = require('express');
const app = express();

app.get('/health', (req, res) => {
    res.json({ status: 'UP' });
});

module.exports = app;
